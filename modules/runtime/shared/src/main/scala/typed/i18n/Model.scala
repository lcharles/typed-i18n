package typed.i18n

enum ArgumentType(val catalogName: String):
  case StringType extends ArgumentType("string")
  case IntType extends ArgumentType("int")
  case LongType extends ArgumentType("long")
  case DecimalType extends ArgumentType("decimal")
  case BooleanType extends ArgumentType("boolean")

object ArgumentType:
  def fromCatalogName(value: String): Option[ArgumentType] =
    ArgumentType.values.find(_.catalogName == value)

final case class MessageDefinition(
    id: String,
    arguments: Vector[(String, ArgumentType)]
):
  lazy val argumentsByName: Map[String, ArgumentType] = arguments.toMap

final case class MessageSchema(
    packageName: String,
    messages: Vector[MessageDefinition]
):
  lazy val messagesById: Map[String, MessageDefinition] =
    messages.iterator.map(message => message.id -> message).toMap

enum MessageArgument:
  case StringValue(value: String)
  case IntValue(value: Int)
  case LongValue(value: Long)
  case DecimalValue(value: BigDecimal)
  case BooleanValue(value: Boolean)

  def rendered: String = this match
    case StringValue(value)  => value
    case IntValue(value)     => value.toString
    case LongValue(value)    => value.toString
    case DecimalValue(value) => value.toString
    case BooleanValue(value) => value.toString

  def argumentType: ArgumentType = this match
    case StringValue(_)  => ArgumentType.StringType
    case IntValue(_)     => ArgumentType.IntType
    case LongValue(_)    => ArgumentType.LongType
    case DecimalValue(_) => ArgumentType.DecimalType
    case BooleanValue(_) => ArgumentType.BooleanType

enum CatalogErrorCode:
  case InvalidJson
  case InvalidSchema
  case InvalidCatalog
  case UnsupportedArgumentType
  case MissingMessage
  case UnknownMessage
  case MissingPlaceholder
  case UnknownPlaceholder
  case MalformedPlaceholder
  case IoError

final case class CatalogError(
    code: CatalogErrorCode,
    detail: String,
    source: Option[String] = None,
    messageId: Option[String] = None,
    argument: Option[String] = None
):
  override def toString: String =
    val location = source.fold("")(value => s"$value: ")
    val message = messageId.fold("")(value => s" message '$value'")
    val argumentName = argument.fold("")(value => s" argument '$value'")
    s"$location${code.toString}:$message$argumentName: $detail"

final case class CatalogCompatibilityError(
    expectedPackage: String,
    actualPackage: String
):
  override def toString: String =
    s"Catalog was validated for schema package '$actualPackage', expected '$expectedPackage'"
