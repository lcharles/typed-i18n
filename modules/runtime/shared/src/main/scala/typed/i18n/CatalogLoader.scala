package typed.i18n

import scala.util.control.NonFatal

object CatalogLoader:
  def parseSchema(
      json: String,
      source: Option[String] = None
  ): Either[Vector[CatalogError], MessageSchema] =
    parseJson(json, source).flatMap(value => decodeSchema(value, source))

  def fromJson(
      schema: MessageSchema,
      json: String,
      source: Option[String] = None,
      strict: Boolean = true
  ): Either[Vector[CatalogError], ValidatedCatalog] =
    parseJson(json, source)
      .flatMap(value => decodeCatalog(value, source))
      .flatMap { case (locale, messages) =>
        validate(schema, locale, messages, source, strict)
      }

  def validate(
      schema: MessageSchema,
      locale: String,
      messages: Map[String, String],
      source: Option[String] = None,
      strict: Boolean = true
  ): Either[Vector[CatalogError], ValidatedCatalog] =
    val schemaIds = schema.messagesById.keySet
    val missingMessages = (schemaIds -- messages.keySet).toVector.sorted.map { id =>
      CatalogError(
        CatalogErrorCode.MissingMessage,
        "Catalog does not define this schema message",
        source,
        Some(id)
      )
    }
    val unknownMessages =
      if strict then
        (messages.keySet -- schemaIds).toVector.sorted.map { id =>
          CatalogError(
            CatalogErrorCode.UnknownMessage,
            "Catalog defines a message that is absent from the schema",
            source,
            Some(id)
          )
        }
      else Vector.empty
    val placeholderErrors = schema.messages.flatMap { definition =>
      messages.get(definition.id).toVector.flatMap { template =>
        Template.placeholders(template) match
          case Left(problem) =>
            Vector(
              CatalogError(
                CatalogErrorCode.MalformedPlaceholder,
                problem,
                source,
                Some(definition.id)
              )
            )
          case Right(actual) =>
            val expected = definition.argumentsByName.keySet
            val missing = (expected -- actual).toVector.sorted.map { name =>
              CatalogError(
                CatalogErrorCode.MissingPlaceholder,
                "Schema argument is not referenced by the catalog template",
                source,
                Some(definition.id),
                Some(name)
              )
            }
            val unknown = (actual -- expected).toVector.sorted.map { name =>
              CatalogError(
                CatalogErrorCode.UnknownPlaceholder,
                "Catalog placeholder is not declared by the schema",
                source,
                Some(definition.id),
                Some(name)
              )
            }
            missing ++ unknown
      }
    }
    val errors = missingMessages ++ unknownMessages ++ placeholderErrors
    if errors.isEmpty then Right(ValidatedCatalog(locale, schema, messages))
    else Left(errors)

  private def parseJson(
      json: String,
      source: Option[String]
  ): Either[Vector[CatalogError], ujson.Value] =
    try Right(ujson.read(json))
    catch
      case NonFatal(error) =>
        Left(
          Vector(
            CatalogError(
              CatalogErrorCode.InvalidJson,
              error.getMessage,
              source
            )
          )
        )

  private def decodeSchema(
      value: ujson.Value,
      source: Option[String]
  ): Either[Vector[CatalogError], MessageSchema] =
    try
      val root = value.obj
      val packageName = requiredString(root, "package")
      val messagesObject = requiredObject(root, "messages")
      val errors = Vector.newBuilder[CatalogError]
      val messages = messagesObject.value.toVector.sortBy(_._1).flatMap { case (messageId, rawDefinition) =>
        try
          val definition = rawDefinition.obj
          val rawArguments = requiredObject(definition, "arguments")
          val arguments = rawArguments.value.toVector.sortBy(_._1).flatMap { case (argumentName, rawType) =>
            val typeName = rawType.str
            ArgumentType.fromCatalogName(typeName) match
              case Some(argumentType) => Some(argumentName -> argumentType)
              case None =>
                errors += CatalogError(
                  CatalogErrorCode.UnsupportedArgumentType,
                  s"Unsupported type '$typeName'; supported types are string, int, long, decimal, boolean",
                  source,
                  Some(messageId),
                  Some(argumentName)
                )
                None
          }
          Some(MessageDefinition(messageId, arguments))
        catch
          case NonFatal(error) =>
            errors += CatalogError(
              CatalogErrorCode.InvalidSchema,
              error.getMessage,
              source,
              Some(messageId)
            )
            None
      }
      val builtErrors = errors.result()
      if builtErrors.nonEmpty then Left(builtErrors)
      else Right(MessageSchema(packageName, messages))
    catch
      case NonFatal(error) =>
        Left(Vector(CatalogError(CatalogErrorCode.InvalidSchema, error.getMessage, source)))

  private def decodeCatalog(
      value: ujson.Value,
      source: Option[String]
  ): Either[Vector[CatalogError], (String, Map[String, String])] =
    try
      val root = value.obj
      val locale = requiredString(root, "locale")
      val messages = requiredObject(root, "messages").value.iterator.map { case (id, rawTemplate) =>
        id -> rawTemplate.str
      }.toMap
      Right(locale -> messages)
    catch
      case NonFatal(error) =>
        Left(Vector(CatalogError(CatalogErrorCode.InvalidCatalog, error.getMessage, source)))

  private def requiredString(objectValue: ujson.Obj, field: String): String =
    objectValue.value.getOrElse(field, throw IllegalArgumentException(s"Missing '$field' field")).str

  private def requiredObject(objectValue: ujson.Obj, field: String): ujson.Obj =
    objectValue.value.getOrElse(field, throw IllegalArgumentException(s"Missing '$field' field")).obj
