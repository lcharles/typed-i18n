package typed.i18n

/** Case-insensitive exact matching, then progressively shorter language tags.
  * Regional variants are never chosen arbitrarily: fr-CA can match fr, but
  * fr does not implicitly choose fr-FR. Applications supply their own fallback.
  */
object LocaleMatching:
  def lookup(preferred: Seq[String], available: Seq[String]): Option[String] =
    preferred.iterator.flatMap { requested =>
      candidates(requested.trim).iterator.flatMap { candidate =>
        available.find(_.equalsIgnoreCase(candidate))
      }.take(1)
    }.take(1).toList.headOption

  private def candidates(tag: String): Vector[String] =
    if !tag.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*") then Vector.empty
    else
      val result = Vector.newBuilder[String]
      var parts = tag.split("-").toVector
      while parts.nonEmpty do
        result += parts.mkString("-")
        parts = parts.dropRight(1)
        // A dangling extension singleton (such as 'u') is removed with its value.
        if parts.lastOption.exists(_.length == 1) then parts = parts.dropRight(1)
      result.result()
