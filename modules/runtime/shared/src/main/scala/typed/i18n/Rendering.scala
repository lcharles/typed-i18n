package typed.i18n

trait MessageRenderer:
  def render(
      messageId: String,
      arguments: Map[String, MessageArgument] = Map.empty
  ): String

final class ValidatedCatalog private[i18n] (
    val locale: String,
    val schema: MessageSchema,
    private val templates: Map[String, String]
):
  val renderer: MessageRenderer = CatalogMessageRenderer(schema, templates)

private final class CatalogMessageRenderer(
    schema: MessageSchema,
    templates: Map[String, String]
) extends MessageRenderer:
  override def render(
      messageId: String,
      arguments: Map[String, MessageArgument]
  ): String =
    val definition = schema.messagesById.getOrElse(
      messageId,
      throw IllegalArgumentException(s"Unknown message '$messageId'")
    )
    val expected = definition.argumentsByName
    val missing = expected.keySet -- arguments.keySet
    val unknown = arguments.keySet -- expected.keySet
    if missing.nonEmpty then
      throw IllegalArgumentException(
        s"Message '$messageId' is missing arguments: ${missing.toVector.sorted.mkString(", ")}"
      )
    if unknown.nonEmpty then
      throw IllegalArgumentException(
        s"Message '$messageId' has unknown arguments: ${unknown.toVector.sorted.mkString(", ")}"
      )
    arguments.toVector.sortBy(_._1).foreach { case (name, value) =>
      val expectedType = expected(name)
      if value.argumentType != expectedType then
        throw IllegalArgumentException(
          s"Message '$messageId' argument '$name' expected ${expectedType.catalogName}, got ${value.argumentType.catalogName}"
        )
    }
    Template.render(templates(messageId), arguments)

private[i18n] object Template:
  private val Placeholder = "\\{([A-Za-z_][A-Za-z0-9_]*)\\}".r

  def placeholders(template: String): Either[String, Set[String]] =
    val matches = Placeholder.findAllMatchIn(template).toVector
    val covered = Array.fill(template.length)(false)
    matches.foreach { matched =>
      var index = matched.start
      while index < matched.end do
        covered(index) = true
        index += 1
    }
    val malformedAt = template.indices.find(index =>
      (template.charAt(index) == '{' || template.charAt(index) == '}') && !covered(index)
    )
    malformedAt match
      case Some(index) => Left(s"Malformed placeholder near character $index")
      case None        => Right(matches.iterator.map(_.group(1)).toSet)

  def render(template: String, arguments: Map[String, MessageArgument]): String =
    val result = new StringBuilder
    var previousEnd = 0
    Placeholder.findAllMatchIn(template).foreach { matched =>
      result.append(template.substring(previousEnd, matched.start))
      result.append(arguments(matched.group(1)).rendered)
      previousEnd = matched.end
    }
    result.append(template.substring(previousEnd))
    result.result()
