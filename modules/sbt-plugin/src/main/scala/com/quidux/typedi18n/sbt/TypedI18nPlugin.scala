package com.quidux.typedi18n.sbt

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import sbt._
import sbt.Keys._

object TypedI18nPlugin extends AutoPlugin {
  object autoImport {
    val typedI18nSchema = settingKey[File]("The typed-i18n message schema JSON file")
    val typedI18nCatalogDirectory = settingKey[File]("Directory containing locale catalog JSON files")
    val typedI18nBundleCatalogs = settingKey[Boolean]("Compile validated catalogs into the generated API (default true)")
    val typedI18nGenerate = taskKey[Seq[File]]("Validate catalogs and generate the typed Scala API")
    val typedI18nValidate = taskKey[Unit]("Validate every locale catalog against the schema")
  }

  import autoImport._

  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = noTrigger

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    typedI18nSchema := (Compile / sourceDirectory).value / "i18n" / "messages.schema.json",
    typedI18nCatalogDirectory := (Compile / sourceDirectory).value / "i18n" / "catalogs",
    typedI18nBundleCatalogs := true,
    typedI18nValidate := {
      validateAll(typedI18nSchema.value, typedI18nCatalogDirectory.value, streams.value.log)
      ()
    },
    typedI18nGenerate := {
      val schemaFile = typedI18nSchema.value
      val catalogDirectory = typedI18nCatalogDirectory.value
      val log = streams.value.log
      val (schema, catalogs) = validateAll(schemaFile, catalogDirectory, log)
      if (!typedI18nBundleCatalogs.value && catalogs.exists(_.fluent.nonEmpty))
        sys.error("Fluent catalogs require typedI18nBundleCatalogs := true; runtime Fluent loading is not supported")
      val output = (Compile / sourceManaged).value / "typed-i18n" / "Strings.scala"
      val content = ScalaGenerator.generate(schema, if (typedI18nBundleCatalogs.value) catalogs else Vector.empty)
      writeIfChanged(output, content)
      log.info(s"typed-i18n generated ${output.getPath}")
      Seq(output)
    },
    Compile / sourceGenerators += typedI18nGenerate.taskValue
  )

  private def validateAll(schemaFile: File, catalogDirectory: File, log: Logger): (MessageSchema, Vector[LocaleCatalog]) = {
    if (!schemaFile.isFile)
      sys.error(s"typed-i18n schema not found: ${schemaFile.getPath}")
    if (!catalogDirectory.isDirectory)
      sys.error(s"typed-i18n catalog directory not found: ${catalogDirectory.getPath}")
    val schema = resultOrFail(JsonFiles.readSchema(schemaFile))
    val catalogFiles = ((catalogDirectory ** "*.json").get ++ (catalogDirectory ** "*.ftl").get)
      .filter(_.isFile).sortBy(_.getPath)
    if (catalogFiles.isEmpty)
      sys.error(s"typed-i18n found no .json or .ftl catalogs in ${catalogDirectory.getPath}")
    val catalogs = Vector.newBuilder[(File, LocaleCatalog)]
    val errors = catalogFiles.flatMap { file =>
      val parsed = if (file.getName.endsWith(".ftl")) FluentCatalog.read(file, schema) else JsonFiles.readCatalog(file)
      parsed match {
        case Left(parseErrors) => parseErrors
        case Right(catalog) =>
          catalogs += file -> catalog
          if (catalog.fluent.nonEmpty) {
            try { PluralCompiler.rules(catalog.locale); Vector.empty }
            catch { case scala.util.control.NonFatal(error) => Vector(Diagnostic(file, error.getMessage)) }
          } else CatalogValidation.validate(schema, catalog, file)
      }
    }
    val parsed = catalogs.result()
    val allErrors = errors ++ CatalogCompiler.validateLocales(parsed)
    if (allErrors.nonEmpty)
      sys.error("typed-i18n validation failed:\n" + allErrors.mkString("\n"))
    log.info(s"typed-i18n validated ${catalogFiles.size} catalog(s)")
    schema -> parsed.map(_._2).sortBy(_.locale)
  }

  private def resultOrFail[A](result: Either[Vector[Diagnostic], A]): A = result match {
    case Right(value) => value
    case Left(errors) => sys.error("typed-i18n schema is invalid:\n" + errors.mkString("\n"))
  }

  private def writeIfChanged(file: File, content: String): Unit = {
    val bytes = content.getBytes(StandardCharsets.UTF_8)
    val unchanged = file.isFile && java.util.Arrays.equals(Files.readAllBytes(file.toPath), bytes)
    if (!unchanged) {
      Files.createDirectories(file.toPath.getParent)
      Files.write(file.toPath, bytes)
    }
  }
}
