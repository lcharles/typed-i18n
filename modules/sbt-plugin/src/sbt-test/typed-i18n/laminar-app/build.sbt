import com.quidux.typedi18n.sbt.TypedI18nPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin, TypedI18nPlugin)
  .settings(
    scalaVersion := "3.9.0",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.quidux" %%% "typed-i18n-runtime" % sys.props("plugin.version"),
      "com.raquo" %%% "laminar" % "17.2.1"
    )
  )
