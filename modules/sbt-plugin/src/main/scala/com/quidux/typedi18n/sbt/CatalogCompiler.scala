package com.quidux.typedi18n.sbt

import java.io.File
import java.util.Locale

private[sbt] sealed trait TemplatePart
private[sbt] object TemplatePart {
  final case class Text(value: String) extends TemplatePart
  final case class Argument(name: String) extends TemplatePart
}

/** The small JSON frontend lowers templates to parts before source generation. */
private[sbt] object CatalogCompiler {
  private val Placeholder = "\\{([A-Za-z_][A-Za-z0-9_]*)\\}".r

  def parse(template: String): Either[String, Vector[TemplatePart]] = {
    val parts = Vector.newBuilder[TemplatePart]
    var previousEnd = 0
    Placeholder.findAllMatchIn(template).foreach { matched =>
      parts += TemplatePart.Text(template.substring(previousEnd, matched.start))
      parts += TemplatePart.Argument(matched.group(1))
      previousEnd = matched.end
    }
    parts += TemplatePart.Text(template.substring(previousEnd))
    val result = parts.result()
    if (result.exists {
      case TemplatePart.Text(value) => value.exists(char => char == '{' || char == '}')
      case _ => false
    }) Left("Malformed placeholder: expected {argumentName}")
    else Right(result)
  }

  def localeCase(tag: String): String =
    tag.split("-").map { part =>
      val lower = part.toLowerCase(Locale.ROOT)
      lower.head.toUpper + lower.tail
    }.mkString

  def validateLocales(catalogs: Vector[(File, LocaleCatalog)]): Vector[Diagnostic] = {
    val errors = Vector.newBuilder[Diagnostic]
    // A deliberately small language-tag syntax, not a registry of BCP 47 subtags.
    catalogs.foreach { case (file, catalog) =>
      if (!catalog.locale.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*"))
        errors += Diagnostic(file, s"Invalid locale '${catalog.locale}'; expected a hyphenated language tag such as en or fr-CH")
    }
    catalogs.groupBy(_._2.locale.toLowerCase(Locale.ROOT)).toVector.sortBy(_._1).foreach {
      case (tag, duplicates) if duplicates.size > 1 =>
        duplicates.foreach { case (file, _) =>
          errors += Diagnostic(file, s"Duplicate locale '$tag'; each locale must have exactly one catalog")
        }
      case _ => ()
    }
    val valid = catalogs.filter(_._2.locale.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*"))
    valid.groupBy(entry => localeCase(entry._2.locale)).toVector.sortBy(_._1).foreach {
      case (name, entries) if entries.map(_._2.locale.toLowerCase(Locale.ROOT)).distinct.size > 1 =>
        entries.foreach { case (file, catalog) =>
          errors += Diagnostic(file, s"Locale '${catalog.locale}' collides as generated Locale case '$name'")
        }
      case _ => ()
    }
    errors.result()
  }
}
