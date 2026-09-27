package com.quidux.typedi18n.sbt

import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.ULocale
import scala.collection.JavaConverters._

/** CLDR data is a build dependency only. Emit arithmetic, never runtime rule strings. */
private[sbt] object PluralCompiler {
  final case class Relation(operand: String, modulus: Option[String], negated: Boolean,
      ranges: Vector[(String, String)], integersOnly: Boolean) {
    def scalaCode: String = {
      val value = modulus.fold("p." + operand)(m => s"(p.$operand % BigDecimal(${ScalaGenerator.literal(m)}))")
      val member = ranges.map { case (low, high) =>
        if (low == high) s"$value == BigDecimal(${ScalaGenerator.literal(low)})"
        else s"($value >= BigDecimal(${ScalaGenerator.literal(low)}) && $value <= BigDecimal(${ScalaGenerator.literal(high)}))"
      }.mkString("(", " || ", ")")
      val result = if (integersOnly) s"($value.isWhole && $member)" else member
      if (negated) s"!$result" else result
    }
    def evaluate(operands: Map[String, BigDecimal]): Boolean = {
      val value = modulus.fold(operands(operand))(m => operands(operand) % BigDecimal(m))
      val member = (!integersOnly || value.isWhole) && ranges.exists { case (low, high) =>
        value >= BigDecimal(low) && value <= BigDecimal(high)
      }
      if (negated) !member else member
    }
  }
  final case class Rule(category: String, alternatives: Vector[Vector[Relation]]) {
    def scalaCode: String = alternatives.map(_.map(_.scalaCode).mkString("(", " && ", ")")).mkString("(", " || ", ")")
    def evaluate(operands: Map[String, BigDecimal]): Boolean = alternatives.exists(_.forall(_.evaluate(operands)))
  }
  private val RelationSyntax = "([nivwftce])(?: (?:%|mod) ([0-9]+))? (not within|not in|within|in|!=|=|is not|is) (.+)".r

  def rules(locale: String): Vector[Rule] = {
    val tag = if (locale == "root" || locale == "und") ULocale.ROOT else ULocale.forLanguageTag(locale)
    require(tag == ULocale.ROOT || PluralRules.getAvailableULocales.exists(_.getLanguage == tag.getLanguage),
      s"No CLDR plural data for locale '$locale'")
    val rules = PluralRules.forLocale(tag)
    rules.getKeywords.asScala.toVector.sorted.filterNot(_ == "other").map { category =>
      val condition = rules.getRules(category)
      val alternatives = condition.split(" or ").toVector.map(_.split(" and ").toVector.map {
        case RelationSyntax(operand, modulus, operator, values) =>
          val ranges = values.split(",").toVector.map { range =>
            val ends = range.trim.split("\\.\\.")
            require(ends.length <= 2 && ends.forall(_.matches("[0-9]+")), s"Unsupported CLDR range '$range'")
            ends.head -> ends.last
          }
          Relation(operand, Option(modulus), operator.contains("not") || operator == "!=", ranges,
            operator != "within" && operator != "not within")
        case unsupported => sys.error(s"Unsupported CLDR plural condition '$unsupported' for '$locale'")
      })
      Rule(category, alternatives)
    }
  }

  def generate(locale: String): String = {
    val conditions = rules(locale)
    val result = new StringBuilder("    private def pluralCategory(value: BigDecimal): String =\n      val p = new PluralOperands(value)\n")
    conditions.zipWithIndex.foreach { case (rule, index) =>
      result.append(s"      ${if (index == 0) "if" else "else if"} ${rule.scalaCode} then ${ScalaGenerator.literal(rule.category)}\n")
    }
    result.append(if (conditions.isEmpty) "      \"other\"\n\n" else "      else \"other\"\n\n")
    result.result()
  }
}
