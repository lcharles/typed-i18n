package typed.i18n

class CatalogLoaderSuite extends munit.FunSuite:
  private val schemaJson =
    """{
      |  "package": "example.i18n",
      |  "messages": {
      |    "account.welcome": { "arguments": { "userName": "string" } },
      |    "survey.answerCount": { "arguments": { "count": "int" } },
      |    "values.all": { "arguments": {
      |      "longValue": "long", "decimalValue": "decimal", "enabled": "boolean"
      |    } }
      |  }
      |}""".stripMargin

  private val schema = CatalogLoader.parseSchema(schemaJson).fold(
    errors => fail(errors.mkString("\n")),
    identity
  )

  test("loads, validates and renders a catalog") {
    val catalogJson =
      """{
        |  "locale": "fr",
        |  "messages": {
        |    "account.welcome": "Bienvenue, {userName}",
        |    "survey.answerCount": "{count} réponses",
        |    "values.all": "{longValue}/{decimalValue}/{enabled}"
        |  }
        |}""".stripMargin
    val catalog = CatalogLoader.fromJson(schema, catalogJson).fold(
      errors => fail(errors.mkString("\n")),
      identity
    )
    assertEquals(catalog.locale, "fr")
    assertEquals(
      catalog.renderer.render(
        "account.welcome",
        Map("userName" -> MessageArgument.StringValue("Lucas"))
      ),
      "Bienvenue, Lucas"
    )
    assertEquals(
      catalog.renderer.render(
        "values.all",
        Map(
          "longValue" -> MessageArgument.LongValue(7L),
          "decimalValue" -> MessageArgument.DecimalValue(BigDecimal("1.25")),
          "enabled" -> MessageArgument.BooleanValue(true)
        )
      ),
      "7/1.25/true"
    )
  }

  test("reports missing messages and placeholder mismatches together") {
    val invalid =
      """{
        |  "locale": "en",
        |  "messages": {
        |    "account.welcome": "Hello, {name}",
        |    "survey.answerCount": "{count} answers",
        |    "extra.message": "extra"
        |  }
        |}""".stripMargin
    val errors = CatalogLoader.fromJson(schema, invalid).swap.getOrElse(Vector.empty)
    assert(errors.exists(error =>
      error.code == CatalogErrorCode.MissingMessage && error.messageId.contains("values.all")
    ))
    assert(errors.exists(error =>
      error.code == CatalogErrorCode.MissingPlaceholder && error.argument.contains("userName")
    ))
    assert(errors.exists(error =>
      error.code == CatalogErrorCode.UnknownPlaceholder && error.argument.contains("name")
    ))
    assert(errors.exists(error =>
      error.code == CatalogErrorCode.UnknownMessage && error.messageId.contains("extra.message")
    ))
  }

  test("unsupported schema argument types identify message and argument") {
    val invalid =
      """{
        |  "package": "example.i18n",
        |  "messages": {
        |    "event.when": { "arguments": { "timestamp": "instant" } }
        |  }
        |}""".stripMargin
    val error = CatalogLoader.parseSchema(invalid).swap.getOrElse(Vector.empty).head
    assertEquals(error.code, CatalogErrorCode.UnsupportedArgumentType)
    assertEquals(error.messageId, Some("event.when"))
    assertEquals(error.argument, Some("timestamp"))
    assert(error.toString.contains("instant"))
  }

  test("rejects malformed placeholders") {
    val invalid =
      """{
        |  "locale": "en",
        |  "messages": {
        |    "account.welcome": "Hello, {userName",
        |    "survey.answerCount": "{count} answers",
        |    "values.all": "{longValue}/{decimalValue}/{enabled}"
        |  }
        |}""".stripMargin
    val errors = CatalogLoader.fromJson(schema, invalid).swap.getOrElse(Vector.empty)
    assert(errors.exists(_.code == CatalogErrorCode.MalformedPlaceholder))
  }
