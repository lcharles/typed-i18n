package example

import example.i18n.Strings
import java.nio.file.Paths
import typed.i18n.JvmCatalogLoader
import scala.compiletime.testing.typeCheckErrors

object Main:
  def main(args: Array[String]): Unit =
    val bundled = Strings.forLocale(Strings.Locale.Fr)
    val expectedSave = if args.contains("updated") then "Sauvegarder" else "Enregistrer"
    assert(bundled.common.save == expectedSave)
    assert(bundled.account.welcome("Lucas") == "Bienvenue, Lucas")
    assert(bundled.survey.answerCount(12) == "12 réponses")
    assert(Strings.forLocale(Strings.Locale.En).common.save == "Save")
    assert(Strings.localeFor(Seq("fr-CH", "en"), Strings.Locale.En) == Strings.Locale.Fr)
    assert(Strings.localeFor(Seq("de"), Strings.Locale.Fr) == Strings.Locale.Fr)
    assert(Strings.availableLocales.map(_.tag) == Vector("en", "fr"))
    assert(bundled.metrics.summary(true, BigDecimal("1.25"), 9000000000L) ==
      "true / 1.25 / 9000000000 / 9000000000")

    assert(typeCheckErrors("example.i18n.Strings.forLocale(example.i18n.Strings.Locale.Fr).common.doesNotExist").nonEmpty)
    assert(typeCheckErrors("example.i18n.Strings.forLocale(example.i18n.Strings.Locale.Fr).common.svae").nonEmpty)
    assert(typeCheckErrors("example.i18n.Strings.forLocale(example.i18n.Strings.Locale.Fr).account.welcome()").nonEmpty)
    assert(typeCheckErrors("example.i18n.Strings.forLocale(example.i18n.Strings.Locale.Fr).account.welcome(\"a\", \"b\")").nonEmpty)
    assert(typeCheckErrors("example.i18n.Strings.forLocale(example.i18n.Strings.Locale.Fr).survey.answerCount(\"12\")").nonEmpty)

    // External catalogs remain an optional path and render identically.
    val catalog = JvmCatalogLoader
      .load(Strings.schema, Paths.get("src/main/i18n/catalogs/fr.json"))
      .fold(errors => throw IllegalArgumentException(errors.mkString("\n")), identity)
    val strings = Strings
      .fromCatalog(catalog)
      .fold(error => throw IllegalArgumentException(error.toString), identity)

    assert(strings.common.save == expectedSave)
    assert(strings.common.cancel == "Annuler")
    assert(strings.account.welcome("Lucas") == "Bienvenue, Lucas")
    assert(strings.survey.answerCount(12) == "12 réponses")
    val trickyName = "$1 \\ {notAPlaceholder}\n\"Lucas\""
    assert(bundled.account.welcome(trickyName) == strings.account.welcome(trickyName))
    assert(bundled.common.escaped == strings.common.escaped)
    println("typed-i18n basic fixture passed")
