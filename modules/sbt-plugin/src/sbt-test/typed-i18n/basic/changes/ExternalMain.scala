package example

import example.i18n.Strings
import java.nio.file.Paths
import typed.i18n.JvmCatalogLoader

object Main:
  def main(args: Array[String]): Unit =
    val catalog = JvmCatalogLoader
      .load(Strings.schema, Paths.get("src/main/i18n/catalogs/fr.json"))
      .fold(errors => throw IllegalArgumentException(errors.mkString("\n")), identity)
    val strings = Strings.unsafeFromCatalog(catalog)
    assert(strings.common.save == "Sauvegarder")
    assert(strings.account.welcome("Lucas") == "Bienvenue, Lucas")
