package com.quidux.typedi18n.sbt

import java.io.File

class ValidationSuite extends munit.FunSuite {
  private val file = new File("fr.json")
  private val schema = MessageSchema(
    "example.i18n",
    Vector(
      MessageDefinition("account.welcome", Vector("userName" -> ArgumentType.StringType)),
      MessageDefinition("common.save", Vector.empty)
    )
  )

  test("catalog diagnostics include file, message and placeholder") {
    val catalog = LocaleCatalog(
      "fr",
      Map(
        "account.welcome" -> "Bienvenue, {name}",
        "extra.message" -> "Extra"
      )
    )
    val errors = CatalogValidation.validate(schema, catalog, file)
    assert(errors.exists(_.toString.contains("fr.json: message 'common.save'")))
    assert(errors.exists(_.toString.contains("argument 'userName'")))
    assert(errors.exists(_.toString.contains("argument 'name'")))
    assert(errors.exists(_.toString.contains("message 'extra.message'")))
  }

  test("identifier validation rejects reserved words and namespace collisions") {
    val messages = Vector(
      MessageDefinition("common.type", Vector.empty),
      MessageDefinition("common.renderer", Vector.empty),
      MessageDefinition("foo.value", Vector.empty),
      MessageDefinition("Foo.other", Vector.empty)
    )
    val errors = IdentifierValidation.validate(file, "example.i18n", messages)
    assert(errors.exists(_.detail.contains("reserved Scala identifier 'type'")))
    assert(errors.exists(_.detail.contains("collides with a generated member")))
    assert(errors.exists(_.detail.contains("collide as generated class 'FooStrings'")))
  }

  test("bundling rejects duplicate, invalid, and colliding locales with filenames") {
    val catalogs = Vector(
      new File("a.json") -> LocaleCatalog("fr", Map.empty),
      new File("b.json") -> LocaleCatalog("FR", Map.empty),
      new File("c.json") -> LocaleCatalog("fr_CH", Map.empty),
      new File("d.json") -> LocaleCatalog("en-a-1", Map.empty),
      new File("e.json") -> LocaleCatalog("en-a1", Map.empty)
    )
    val errors = CatalogCompiler.validateLocales(catalogs)
    assert(errors.exists(error => error.file.getName == "b.json" && error.detail.contains("Duplicate locale")))
    assert(errors.exists(error => error.file.getName == "c.json" && error.detail.contains("Invalid locale")))
    assert(errors.exists(_.detail.contains("collides as generated Locale case 'EnA1'")))
  }

  test("template frontend preserves repeated arguments, punctuation and empty text") {
    assertEquals(CatalogCompiler.parse("{name}!{name}"), Right(Vector(
      TemplatePart.Text(""), TemplatePart.Argument("name"), TemplatePart.Text("!"),
      TemplatePart.Argument("name"), TemplatePart.Text("")
    )))
    assert(CatalogCompiler.parse("bad {name").isLeft)
    assert(CatalogCompiler.parse("{{name}}").isLeft)
  }
}
