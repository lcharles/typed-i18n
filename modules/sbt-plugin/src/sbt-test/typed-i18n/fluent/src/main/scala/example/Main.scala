package example

import example.i18n.Strings
import example.i18n.Strings.Locale

object Main:
  def main(args: Array[String]): Unit =
    val en = Strings.forLocale(Locale.En)
    assert(en eq Strings.forLocale(Locale.En))
    assert(en.common eq Strings.forLocale(Locale.En).common)
    assert(en.common.reference == "Save / Save")
    assert(en.common.constant("unused") == "Constant")
    assert(en.account.welcome("formal", "Lucas") == "Hello, Lucas, dear friend!")
    assert(en.account.welcome("casual", "$1 {x}") == "Hi, $1 {x}")
    assert(en.survey.answerCount(0) == "exact zero")
    assert(en.survey.answerCount(1) == "exact one")
    assert(en.survey.answerCount(2) == "other")
    assert(en.metrics.decimal(BigDecimal("1")) == "one")
    assert(en.metrics.decimal(BigDecimal("1.0")) == "other")
    assert(en.metrics.decimal(BigDecimal("1.25")) == "other")
    assert(en.metrics.large(Long.MaxValue) == "maximum")
    assert(en.metrics.large(Long.MaxValue - 1) == "9223372036854775806")
    assert(en.metrics.large(Long.MinValue) == "-9223372036854775808")
    val fr = Strings.forLocale(Locale.Fr)
    assert(fr.survey.answerCount(0) == "one")
    assert(fr.survey.answerCount(1) == "one")
    assert(fr.survey.answerCount(2) == "other")
    assert(fr.survey.answerCount(1000000) == "many")
    val ru = Strings.forLocale(Locale.Ru)
    assert(ru.survey.answerCount(1) == "one")
    assert(ru.survey.answerCount(2) == "few")
    assert(ru.survey.answerCount(5) == "many")
    assert(ru.survey.answerCount(11) == "many")
    assert(ru.survey.answerCount(21) == "one")
    assert(ru.survey.answerCount(-22) == "few")
    assert(ru.metrics.decimal(BigDecimal("1.5")) == "other")
    val ar = Strings.forLocale(Locale.Ar)
    assert(Vector(0, 1, 2, 3, 11, 100).map(ar.survey.answerCount) ==
      Vector("zero", "one", "two", "few", "many", "other"))
    val pl = Strings.forLocale(Locale.Pl)
    assert(Vector(1, 2, 5, 21, 22).map(pl.survey.answerCount) == Vector("one", "few", "many", "many", "few"))
    println("Fluent bundled selectors, terms, decimals, exact Longs and singleton identity passed")
