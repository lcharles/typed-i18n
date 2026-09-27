import com.quidux.typedi18n.sbt.TypedI18nPlugin
enablePlugins(TypedI18nPlugin)
scalaVersion := "3.9.0"
libraryDependencies += "com.quidux" %% "typed-i18n-runtime" % sys.props("plugin.version")
