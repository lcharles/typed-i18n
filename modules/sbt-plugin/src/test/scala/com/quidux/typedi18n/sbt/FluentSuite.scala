package com.quidux.typedi18n.sbt

import sbt.IO
import java.io.File

class FluentSuite extends munit.FunSuite {
  private val schema = MessageSchema("example.i18n", Vector(
    MessageDefinition("survey.answerCount", Vector("count" -> ArgumentType.IntType)),
    MessageDefinition("account.welcome", Vector("userName" -> ArgumentType.StringType))
  ))
  private def compile(source: String, shape: MessageSchema = schema): Either[Vector[Diagnostic], LocaleCatalog] =
    IO.withTemporaryDirectory { directory =>
      val file = new File(directory, "en.ftl")
      IO.write(file, source)
      FluentCatalog.read(file, shape)
    }

  test("reference parser, plural selectors, term parameters and omitted arguments") {
    val source = """-greeting = Hello
      |-role = { $form ->
      |    [short] friend
      |   *[long] dear friend
      |    }
      |account-welcome = { -greeting }, { -role(form: "short") }!
      |survey-answerCount = { $count ->
      |    [one] One answer
      |    [0] No answers yet
      |   *[other] { $count } answers
      |    }
      |""".stripMargin
    val catalog = compile(source).fold(errors => fail(errors.mkString("\n")), identity)
    val generated = ScalaGenerator.generate(schema, Vector(catalog))
    assert(generated.contains("No answers yet"))
    assert(generated.contains("pluralCategory(selected)"))
    assert(generated.contains("BundledEn.strings"))
    assert(generated.contains("lazy val schema"))
    assert(generated.contains("friend"))
  }

  test("syntax errors include file and line, unsupported functions are explicit") {
    val malformed = compile("account-welcome = { $userName\nsurvey-answerCount = Count")
    assert(malformed.left.get.exists(e => e.file.getName == "en.ftl" && e.detail.contains("line")))
    val unsupported = compile("account-welcome = { NUMBER($userName) }\nsurvey-answerCount = Count")
    assert(unsupported.left.get.exists(_.detail.contains("FunctionReference")))
  }

  test("unknown variables, missing messages, bad selector types and duplicate variants fail") {
    assert(compile("account-welcome = { $wrong }\nsurvey-answerCount = Count").left.get.exists(_.argument.contains("wrong")))
    assert(compile("account-welcome = Hi").left.get.exists(_.messageId.contains("survey.answerCount")))
    assert(compile("""account-welcome = { $userName ->
      | [1] One
      | *[other] Other
      | }
      |survey-answerCount = Count
      |""".stripMargin).isLeft)
    assert(compile("""account-welcome = Hi
      |survey-answerCount = { $count ->
      | [one] One
      | [one] Again
      | *[other] Other
      | }
      |""".stripMargin).isLeft)
  }

  test("references are validated transitively, even in unused terms") {
    val good = "account-welcome = Hello\nsurvey-answerCount = Count\n"
    assert(compile(good + "-unused = { -missing }\n").isLeft)
    assert(compile(good + "-a = { -b }\n-b = { -a }\n").left.get.exists(_.detail.contains("Cyclic")))
    assert(compile(good + "extra = Surprise\n").isLeft)
    assert(compile(good + "account-welcome = Duplicate\n").isLeft)
  }

  test("Fluent string escapes are decoded by the upstream parser") {
    val catalog = compile("account-welcome = { \"\\u0041\" }\nsurvey-answerCount = Count\n").toOption.get
    assertEquals(catalog.fluent.get("account.welcome").parts, Vector(FluentExpression.Text("A")))
  }

  test("term scope does not capture caller variables; argument typos are rejected") {
    val prefix = "-greeting = Hello, { $name }\nsurvey-answerCount = Count\n"
    assert(compile(prefix + "account-welcome = { -greeting }\n").left.get.exists(_.argument.contains("name")))
    assert(compile(prefix + "account-welcome = { -greeting(name: \"Lucas\", typo: \"x\") }\n")
      .left.get.exists(_.argument.contains("typo")))
  }

  test("Fluent ID collisions and attributes are rejected") {
    val shape = MessageSchema("example.i18n", Vector(
      MessageDefinition("a.b", Vector.empty, Some("same")),
      MessageDefinition("a.c", Vector.empty, Some("same"))
    ))
    assert(compile("same = Hi\n", shape).left.get.exists(_.detail.contains("collide")))
    assert(compile("account-welcome = Hi\n    .title = Title\nsurvey-answerCount = Count\n")
      .left.get.exists(_.detail.contains("attributes")))
  }
}
