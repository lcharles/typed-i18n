import com.quidux.typedi18n.sbt.TypedI18nPlugin
import com.quidux.typedi18n.sbt.TypedI18nPlugin.autoImport._

ThisBuild / scalaVersion := "3.9.0"
val fixtureRoot = file(".").getCanonicalFile
val sharedSettings = Seq(
  Compile / scalaSource := fixtureRoot / "src/main/scala",
  typedI18nSchema := fixtureRoot / "src/main/i18n/messages.schema.json",
  typedI18nCatalogDirectory := fixtureRoot / "src/main/i18n/catalogs"
)
lazy val jvm = project.in(file("jvm")).enablePlugins(TypedI18nPlugin)
  .settings(sharedSettings)
  .settings(libraryDependencies += "com.quidux" %% "typed-i18n-runtime" % sys.props("plugin.version"))
lazy val js = project.in(file("js")).enablePlugins(ScalaJSPlugin, TypedI18nPlugin)
  .settings(sharedSettings)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "com.quidux" %%% "typed-i18n-runtime" % sys.props("plugin.version")
  )
lazy val root = project.in(file(".")).aggregate(jvm, js)
