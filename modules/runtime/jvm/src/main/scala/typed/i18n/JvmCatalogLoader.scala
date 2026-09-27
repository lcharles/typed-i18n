package typed.i18n

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.util.control.NonFatal

object JvmCatalogLoader:
  def load(
      schema: MessageSchema,
      path: Path,
      strict: Boolean = true
  ): Either[Vector[CatalogError], ValidatedCatalog] =
    try
      val json = Files.readString(path, StandardCharsets.UTF_8)
      CatalogLoader.fromJson(schema, json, Some(path.toString), strict)
    catch
      case NonFatal(error) =>
        Left(
          Vector(
            CatalogError(
              CatalogErrorCode.IoError,
              error.getMessage,
              Some(path.toString)
            )
          )
        )
