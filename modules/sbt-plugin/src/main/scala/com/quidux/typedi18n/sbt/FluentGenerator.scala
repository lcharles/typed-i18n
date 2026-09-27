package com.quidux.typedi18n.sbt

private[sbt] object FluentGenerator {
  import FluentExpression._
  import ScalaGenerator.literal

  def pattern(value: FluentPattern, indent: String = "          "): String = {
    val expressions = value.parts.map(expression(_, indent))
    if (expressions.isEmpty) "\"\"" else expressions.mkString(" + ")
  }

  private def expression(value: FluentExpression, indent: String): String = value match {
    case Text(text) => literal(text)
    case Number(number) => literal(number)
    case Variable(name) => s"arguments(${literal(name)}).rendered"
    case Pattern(value) => "(" + pattern(value, indent) + ")"
    case Select(selector, variants, numeric) =>
      val selected = if (numeric) selector match {
        case Variable(name) => s"PluralOperands.number(arguments(${literal(name)}))"
        case Number(number) => s"BigDecimal(${literal(number)})"
        case _ => sys.error("Invalid compiled numeric selector")
      } else expression(selector, indent)
      val fallback = variants.find(_.default).get
      // Fluent checks exact numeric keys before grammatical categories, regardless of order.
      val ordered = if (numeric) variants.filter(_.numeric) ++ variants.filterNot(_.numeric) else variants
      val builder = new StringBuilder("({\n")
      builder.append(s"$indent  val selected = $selected\n")
      if (numeric) builder.append(s"$indent  lazy val category = pluralCategory(selected)\n")
      ordered.zipWithIndex.foreach { case (variant, index) =>
        val condition = if (numeric && variant.numeric) s"selected == BigDecimal(${literal(variant.key)})"
          else if (numeric) s"category == ${literal(variant.key)}"
          else s"selected == ${literal(variant.key)}"
        builder.append(s"$indent  ${if (index == 0) "if" else "else if"} $condition then\n")
        builder.append(s"$indent    ${pattern(variant.value, indent + "    ")}\n")
      }
      builder.append(s"$indent  else\n$indent    ${pattern(fallback.value, indent + "    ")}\n$indent})")
      builder.result()
    case Reference(id, _) => sys.error(s"Unresolved Fluent reference '$id'")
  }
}
