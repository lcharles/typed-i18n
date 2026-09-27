package typed.i18n

/** CLDR operands for ordinary (non-compact) numbers. Decimal scale is preserved.
  * Used by generated code; no catalog parsing or locale data loading occurs here.
  */
final class PluralOperands(value: BigDecimal):
  val n: BigDecimal = value.abs
  val i: BigDecimal = n.setScale(0, BigDecimal.RoundingMode.DOWN)
  val v: BigDecimal = BigDecimal(n.scale.max(0))
  private val stripped = n.bigDecimal.stripTrailingZeros
  val w: BigDecimal = BigDecimal(stripped.scale.max(0))
  val f: BigDecimal = BigDecimal(n.bigDecimal.remainder(java.math.BigDecimal.ONE).movePointRight(v.toInt))
  val t: BigDecimal = BigDecimal(stripped.remainder(java.math.BigDecimal.ONE).movePointRight(w.toInt))
  val c: BigDecimal = BigDecimal(0)
  val e: BigDecimal = BigDecimal(0)

object PluralOperands:
  def number(argument: MessageArgument): BigDecimal = argument match
    case MessageArgument.IntValue(value) => BigDecimal(value)
    case MessageArgument.LongValue(value) => BigDecimal(value)
    case MessageArgument.DecimalValue(value) => value
    case _ => throw IllegalArgumentException("Expected a numeric Fluent selector")
