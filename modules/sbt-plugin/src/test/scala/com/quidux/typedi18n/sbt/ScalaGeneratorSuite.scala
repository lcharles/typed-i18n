package com.quidux.typedi18n.sbt

class ScalaGeneratorSuite extends munit.FunSuite {
  test("generates deterministic nested typed accessors") {
    val schema = MessageSchema(
      "example.i18n",
      Vector(
        MessageDefinition("survey.answerCount", Vector("count" -> ArgumentType.IntType)),
        MessageDefinition("common.save", Vector.empty),
        MessageDefinition("account.profile.welcome", Vector("userName" -> ArgumentType.StringType))
      )
    )
    val generated = ScalaGenerator.generate(schema)
    assert(generated.contains("final class Strings private (private val renderer: MessageRenderer)"))
    assert(generated.contains("def save: String"))
    assert(generated.contains("def answerCount(count: Int): String"))
    assert(generated.contains("val profile: AccountProfileStrings"))
    assert(generated.contains("def welcome(userName: String): String"))
    assert(generated.contains(
      "MessageDefinition(\"common.save\", Vector())"
    ))
    assertEquals(ScalaGenerator.generate(schema), generated)
    assertEquals(ScalaGenerator.generate(schema.copy(messages = schema.messages.reverse)), generated)
    assert(!generated.contains("def forLocale"))
  }

  test("bundles parsed templates as readable expressions without runtime loading") {
    val schema = MessageSchema("example.i18n", Vector(
      MessageDefinition("account.welcome", Vector("name" -> ArgumentType.StringType))
    ))
    val catalogs = Vector(
      LocaleCatalog("fr-CH", Map("account.welcome" -> "Salut, {name}! {name}\n\"\\$")),
      LocaleCatalog("en", Map("account.welcome" -> "Hi, {name}"))
    )
    val generated = ScalaGenerator.generate(schema, catalogs)
    assertEquals(ScalaGenerator.generate(schema, catalogs.reverse), generated)
    assert(generated.contains("case FrCh extends Locale(\"fr-CH\")"))
    assert(generated.contains("def forLocale(locale: Locale): Strings"))
    assert(generated.contains("\"Salut, \" + arguments(\"name\").rendered + \"! \""))
    assert(!generated.contains("CatalogLoader"))
    assert(!generated.contains("Template.render"))
    val changed = catalogs.updated(1, catalogs(1).copy(messages = Map("account.welcome" -> "Hello, {name}")))
    assertNotEquals(ScalaGenerator.generate(schema, changed), generated)
  }
}
