package typed.i18n

class PluralOperandsSuite extends munit.FunSuite:
  test("decimal operands preserve visible zeros and use absolute values") {
    val p = new PluralOperands(BigDecimal("-12.0300"))
    assertEquals(Vector(p.n, p.i, p.v, p.w, p.f, p.t),
      Vector("12.0300", "12", "4", "2", "300", "3").map(BigDecimal(_)))
  }
  test("Long and scientific notation retain exact integer values on JVM and JS") {
    assertEquals(PluralOperands.number(MessageArgument.LongValue(Long.MaxValue)), BigDecimal("9223372036854775807"))
    val p = new PluralOperands(BigDecimal("1E+6"))
    assertEquals(p.i, BigDecimal(1000000))
    assertEquals(p.v, BigDecimal(0))
    assertEquals(p.f, BigDecimal(0))
  }
