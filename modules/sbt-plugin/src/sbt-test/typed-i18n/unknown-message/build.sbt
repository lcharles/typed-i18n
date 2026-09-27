import com.quidux.typedi18n.sbt.TypedI18nPlugin

lazy val root = project
  .in(file("."))
  .enablePlugins(TypedI18nPlugin)
  .settings(
    scalaVersion := "3.9.0",
    libraryDependencies +=
      "com.quidux" %% "typed-i18n-runtime" % sys.props("plugin.version")
  )
