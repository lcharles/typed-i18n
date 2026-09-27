package com.quidux.typedi18n.sbt

import java.io.File

private[sbt] object IdentifierValidation {
  private val Identifier = "[A-Za-z_][A-Za-z0-9_]*".r
  private val Reserved = Set(
    "abstract", "case", "catch", "class", "def", "do", "else", "end", "enum",
    "export", "extends", "false", "final", "finally", "for", "given", "if",
    "implicit", "import", "inline", "lazy", "match", "new", "null", "object",
    "opaque", "open", "override", "package", "private", "protected", "return",
    "sealed", "super", "then", "this", "throw", "trait", "transparent", "true",
    "try", "type", "using", "val", "var", "while", "with", "yield"
  )
  private val GeneratedMembers = Set("renderer")

  def validate(
      file: File,
      packageName: String,
      messages: Vector[MessageDefinition]
  ): Vector[Diagnostic] = {
    val errors = Vector.newBuilder[Diagnostic]
    packageName.split("\\.", -1).foreach { segment =>
      if (!valid(segment))
        errors += Diagnostic(file, s"Invalid Scala package segment '$segment'")
    }
    messages.foreach { message =>
      val path = message.id.split("\\.", -1).toVector
      if (path.length < 2)
        errors += Diagnostic(file, "Message IDs must contain at least one dotted namespace", Some(message.id))
      path.foreach { segment =>
        if (!valid(segment))
          errors += Diagnostic(file, s"Invalid or reserved Scala identifier '$segment'", Some(message.id))
        else if (GeneratedMembers(segment))
          errors += Diagnostic(file, s"Scala identifier '$segment' collides with a generated member", Some(message.id))
      }
      message.arguments.foreach { case (name, _) =>
        if (!valid(name))
          errors += Diagnostic(
            file,
            s"Invalid or reserved Scala argument identifier '$name'",
            Some(message.id),
            Some(name)
          )
      }
    }
    messages.groupBy(_.id).collect { case (id, duplicates) if duplicates.size > 1 => id }.foreach { id =>
      errors += Diagnostic(file, "Duplicate message ID", Some(id))
    }
    val paths = messages.map(_.id.split("\\.").toVector)
    paths.foreach { path =>
      val prefixes = (1 until path.length).map(path.take)
      prefixes.find(prefix => paths.contains(prefix)).foreach { collision =>
        errors += Diagnostic(
          file,
          s"Message is also used as namespace '${collision.mkString(".")}'",
          Some(path.mkString("."))
        )
      }
    }
    val namespacePaths = paths.flatMap(path => (1 until path.length).map(path.take)).distinct
    namespacePaths.groupBy(className).collect {
      case (name, collisions) if collisions.distinct.size > 1 => name -> collisions.distinct
    }.foreach { case (name, collisions) =>
      errors += Diagnostic(
        file,
        s"Namespace paths ${collisions.map(_.mkString(".")).mkString(", ")} collide as generated class '$name'"
      )
    }
    errors.result()
  }

  def className(path: Vector[String]): String =
    path.map(capitalize).mkString + "Strings"

  private def capitalize(value: String): String =
    if (value.isEmpty) value else value.head.toUpper + value.tail

  private def valid(value: String): Boolean =
    Identifier.pattern.matcher(value).matches() && !Reserved(value)
}

private[sbt] object CatalogValidation {

  def validate(
      schema: MessageSchema,
      catalog: LocaleCatalog,
      file: File,
      strict: Boolean = true
  ): Vector[Diagnostic] = {
    val schemaById = schema.messages.map(message => message.id -> message).toMap
    val schemaIds = schemaById.keySet
    val errors = Vector.newBuilder[Diagnostic]
    (schemaIds -- catalog.messages.keySet).toVector.sorted.foreach { id =>
      errors += Diagnostic(file, "Catalog does not define this schema message", Some(id))
    }
    if (strict) {
      (catalog.messages.keySet -- schemaIds).toVector.sorted.foreach { id =>
        errors += Diagnostic(file, "Catalog defines a message absent from the schema", Some(id))
      }
    }
    schema.messages.foreach { message =>
      catalog.messages.get(message.id).foreach { template =>
        placeholders(template) match {
          case Left(problem) => errors += Diagnostic(file, problem, Some(message.id))
          case Right(actual) =>
            val expected = message.arguments.map(_._1).toSet
            (expected -- actual).toVector.sorted.foreach { name =>
              errors += Diagnostic(
                file,
                "Schema argument is not referenced by the catalog template",
                Some(message.id),
                Some(name)
              )
            }
            (actual -- expected).toVector.sorted.foreach { name =>
              errors += Diagnostic(
                file,
                "Catalog placeholder is not declared by the schema",
                Some(message.id),
                Some(name)
              )
            }
        }
      }
    }
    errors.result()
  }

  private def placeholders(template: String): Either[String, Set[String]] = {
    CatalogCompiler.parse(template).map(_.collect {
      case TemplatePart.Argument(name) => name
    }.toSet)
  }
}
