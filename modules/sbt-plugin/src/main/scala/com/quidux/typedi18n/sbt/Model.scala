package com.quidux.typedi18n.sbt

private[sbt] sealed trait ArgumentType {
  def scalaType: String
  def constructor: String
}

private[sbt] object ArgumentType {
  case object StringType extends ArgumentType {
    val scalaType = "String"
    val constructor = "StringValue"
  }
  case object IntType extends ArgumentType {
    val scalaType = "Int"
    val constructor = "IntValue"
  }
  case object LongType extends ArgumentType {
    val scalaType = "Long"
    val constructor = "LongValue"
  }
  case object DecimalType extends ArgumentType {
    val scalaType = "BigDecimal"
    val constructor = "DecimalValue"
  }
  case object BooleanType extends ArgumentType {
    val scalaType = "Boolean"
    val constructor = "BooleanValue"
  }

  def parse(value: String): Option[ArgumentType] = value match {
    case "string"  => Some(StringType)
    case "int"     => Some(IntType)
    case "long"    => Some(LongType)
    case "decimal" => Some(DecimalType)
    case "boolean" => Some(BooleanType)
    case _          => None
  }
}

private[sbt] final case class MessageDefinition(
    id: String,
    arguments: Vector[(String, ArgumentType)],
    fluentId: Option[String] = None
) {
  def catalogId: String = fluentId.getOrElse(id.replace('.', '-'))
}

private[sbt] final case class MessageSchema(
    packageName: String,
    messages: Vector[MessageDefinition]
)

private[sbt] final case class LocaleCatalog(
    locale: String,
    messages: Map[String, String],
    fluent: Option[Map[String, FluentPattern]] = None
)

private[sbt] final case class Diagnostic(
    file: java.io.File,
    detail: String,
    messageId: Option[String] = None,
    argument: Option[String] = None
) {
  override def toString: String = {
    val message = messageId.fold("")(id => s" message '$id'")
    val argumentName = argument.fold("")(name => s" argument '$name'")
    s"${file.getPath}:$message$argumentName: $detail"
  }
}
