package com.quidux.typedi18n.sbt

import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.ULocale

class PluralCompilerSuite extends munit.FunSuite {
  test("every ICU cardinal rule can be compiled; integers agree with ICU for every locale") {
    PluralRules.getAvailableULocales.foreach { locale =>
      val rules = PluralCompiler.rules(locale.toLanguageTag)
      val reference = PluralRules.forLocale(locale)
      (0 to 220).foreach { count =>
        val operands = Map("n" -> BigDecimal(count), "i" -> BigDecimal(count),
          "v" -> BigDecimal(0), "w" -> BigDecimal(0), "f" -> BigDecimal(0),
          "t" -> BigDecimal(0), "c" -> BigDecimal(0), "e" -> BigDecimal(0))
        val actual = rules.find(_.evaluate(operands)).map(_.category).getOrElse("other")
        assertEquals(actual, reference.select(count.toDouble), s"$locale count=$count")
      }
    }
  }
}
