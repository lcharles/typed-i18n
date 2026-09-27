import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*

ThisBuild / organization := "com.quidux"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / versionScheme := Some("early-semver")

val ujsonVersion = "4.4.3"
val munitVersion = "1.2.4"

lazy val runtime = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/runtime"))
  .settings(
    name := "typed-i18n-runtime",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "ujson" % ujsonVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
  .jsSettings(
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1"
  )

lazy val runtimeJVM = runtime.jvm
lazy val runtimeJS = runtime.js

lazy val plugin = project
  .in(file("modules/sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-typed-i18n",
    scalaVersion := "2.12.21",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "ujson" % ujsonVersion,
      "com.ibm.icu" % "icu4j" % "78.3",
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      "-Dplugin.version=" + version.value
    ),
    scriptedBufferLog := false
  )

lazy val root = project
  .in(file("."))
  .aggregate(runtimeJVM, runtimeJS, plugin)
  .settings(
    name := "typed-i18n",
    publish / skip := true
  )

addCommandAlias(
  "check",
  ";runtimeJVM/test;runtimeJS/test;plugin/test;runtimeJVM/publishLocal;runtimeJS/publishLocal;plugin/scripted"
)
