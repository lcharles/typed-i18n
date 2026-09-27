package com.quidux.typedi18n.sbt

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.control.NonFatal

private[sbt] object JsonFiles {
  def readSchema(file: File): Either[Vector[Diagnostic], MessageSchema] =
    parse(file).flatMap { value =>
      try {
        val root = value.obj
        val packageName = requiredString(root, "package")
        val rawMessages = requiredObject(root, "messages")
        val errors = Vector.newBuilder[Diagnostic]
        val messages = rawMessages.value.toVector.sortBy(_._1).flatMap {
          case (messageId, rawDefinition) =>
            try {
              val rawArguments = requiredObject(rawDefinition.obj, "arguments")
              val arguments = rawArguments.value.toVector.sortBy(_._1).flatMap {
                case (argumentName, rawType) =>
                  val typeName = rawType.str
                  ArgumentType.parse(typeName) match {
                    case Some(argumentType) => Some(argumentName -> argumentType)
                    case None =>
                      errors += Diagnostic(
                        file,
                        s"Unsupported type '$typeName'; supported types are string, int, long, decimal, boolean",
                        Some(messageId),
                        Some(argumentName)
                      )
                      None
                  }
              }
              val fluentId = rawDefinition.obj.value.get("fluentId").map(_.str)
              Some(MessageDefinition(messageId, arguments, fluentId))
            } catch {
              case NonFatal(error) =>
                errors += Diagnostic(file, error.getMessage, Some(messageId))
                None
            }
        }
        errors ++= IdentifierValidation.validate(file, packageName, messages)
        val builtErrors = errors.result()
        if (builtErrors.nonEmpty) Left(builtErrors)
        else Right(MessageSchema(packageName, messages))
      } catch {
        case NonFatal(error) => Left(Vector(Diagnostic(file, error.getMessage)))
      }
    }

  def readCatalog(file: File): Either[Vector[Diagnostic], LocaleCatalog] =
    parse(file).flatMap { value =>
      try {
        val root = value.obj
        val locale = requiredString(root, "locale")
        val messages = requiredObject(root, "messages").value.iterator.map {
          case (id, template) => id -> template.str
        }.toMap
        Right(LocaleCatalog(locale, messages))
      } catch {
        case NonFatal(error) => Left(Vector(Diagnostic(file, error.getMessage)))
      }
    }

  private def parse(file: File): Either[Vector[Diagnostic], ujson.Value] =
    try {
      val json = new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)
      Right(ujson.read(json))
    } catch {
      case NonFatal(error) => Left(Vector(Diagnostic(file, error.getMessage)))
    }

  private def requiredString(value: ujson.Obj, field: String): String =
    value.value.getOrElse(field, throw new IllegalArgumentException(s"Missing '$field' field")).str

  private def requiredObject(value: ujson.Obj, field: String): ujson.Obj =
    value.value.getOrElse(field, throw new IllegalArgumentException(s"Missing '$field' field")).obj
}
