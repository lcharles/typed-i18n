package com.quidux.typedi18n.sbt

import java.io.File
import scala.sys.process._
import scala.util.control.NonFatal
import sbt.IO

private[sbt] sealed trait FluentExpression
private[sbt] object FluentExpression {
  final case class Text(value: String) extends FluentExpression
  final case class Number(value: String) extends FluentExpression
  final case class Variable(name: String) extends FluentExpression
  final case class Reference(id: String, arguments: Map[String, FluentExpression]) extends FluentExpression
  final case class Pattern(value: FluentPattern) extends FluentExpression
  final case class Select(selector: FluentExpression, variants: Vector[FluentVariant], numeric: Boolean = false)
      extends FluentExpression
}
private[sbt] final case class FluentPattern(parts: Vector[FluentExpression])
private[sbt] final case class FluentVariant(key: String, numeric: Boolean, default: Boolean, value: FluentPattern)

/** The upstream parser owns Fluent syntax; JSON is confined to this AST boundary. */
private[sbt] object FluentCatalog {
  import FluentExpression._

  def read(file: File, schema: MessageSchema): Either[Vector[Diagnostic], LocaleCatalog] = {
    try {
      val ast = IO.withTemporaryDirectory { directory =>
        Vector("fluent-syntax.cjs", "parse-fluent.cjs").foreach { name =>
          val input = getClass.getResourceAsStream("/typed-i18n/" + name)
          if (input == null) sys.error(s"Missing packaged Fluent parser resource '$name'")
          try IO.transfer(input, new File(directory, name)) finally input.close()
        }
        val output = new StringBuilder
        val errors = new StringBuilder
        val process = Process(Seq("node", new File(directory, "parse-fluent.cjs").getPath, file.getAbsolutePath))
        val status = process.!(ProcessLogger(line => output.append(line), line => errors.append(line).append('\n')))
        if (status != 0) sys.error("Fluent parser failed (Node.js must be installed): " + errors.result())
        ujson.read(output.result())
      }
      compile(file, schema, ast)
    } catch {
      case NonFatal(error) => Left(Vector(Diagnostic(file, "Fluent compilation failed: " + error.getMessage)))
    }
  }

  private def compile(file: File, schema: MessageSchema, ast: ujson.Value): Either[Vector[Diagnostic], LocaleCatalog] = {
    val errors = Vector.newBuilder[Diagnostic]
    schema.messages.foreach { message =>
      if (!message.catalogId.matches("[A-Za-z][A-Za-z0-9_-]*"))
        errors += Diagnostic(file, s"Invalid Fluent ID '${message.catalogId}'; set fluentId explicitly", Some(message.id))
    }
    schema.messages.groupBy(_.catalogId).filter(_._2.size > 1).foreach { case (id, _) =>
      errors += Diagnostic(file, s"Messages collide as Fluent ID '$id'; set distinct fluentId values")
    }
    val source = IO.read(file)
    def fail(detail: String, id: String, argument: Option[String] = None): Nothing =
      throw Problem(Diagnostic(file, detail, Some(id), argument))
    def unsupported(node: ujson.Value, id: String): Nothing =
      fail(s"Unsupported Fluent construct '${node("type").str}' in this compiler subset", id)
    def expression(node: ujson.Value, id: String): FluentExpression = node("type").str match {
      case "TextElement" | "StringLiteral" => Text(node("value").str)
      case "NumberLiteral" => Number(node("value").str)
      case "VariableReference" => Variable(node("id")("name").str)
      case "Placeable" => expression(node("expression"), id)
      case "TermReference" | "MessageReference" =>
        if (node.obj.get("attribute").exists(_ != ujson.Null)) fail("Fluent attributes are not supported yet", id)
        val term = node("type").str == "TermReference"
        val args = node.obj.get("arguments").filter(_ != ujson.Null).map { raw =>
          if (raw("positional").arr.nonEmpty) fail("Positional term arguments are not supported", id)
          val named = raw("named").arr.toVector.map { item =>
            item("name")("name").str -> expression(item("value"), id)
          }
          if (named.map(_._1).distinct.size != named.size) fail("Duplicate term argument", id)
          named.toMap
        }.getOrElse(Map.empty[String, FluentExpression])
        Reference((if (term) "-" else "") + node("id")("name").str, args)
      case "SelectExpression" =>
        val variants = node("variants").arr.toVector.map { variant =>
          val key = variant("key")
          val numeric = key("type").str == "NumberLiteral"
          FluentVariant(if (numeric) key("value").str else key("name").str,
            numeric, variant("default").bool, pattern(variant("value"), id))
        }
        if (variants.count(_.default) != 1) fail("A selector requires exactly one default variant", id)
        val keys = variants.map(v => if (v.numeric) "number:" + BigDecimal(v.key).bigDecimal.stripTrailingZeros.toPlainString else "text:" + v.key)
        if (keys.distinct.size != keys.size) fail("Duplicate selector variant key", id)
        Select(expression(node("selector"), id), variants)
      case _ => unsupported(node, id)
    }
    def pattern(node: ujson.Value, id: String): FluentPattern = {
      if (node == ujson.Null) fail("A message must have a value; attributes-only messages are not supported", id)
      FluentPattern(node("elements").arr.toVector.map(expression(_, id)))
    }

    val entries = scala.collection.mutable.LinkedHashMap.empty[String, FluentPattern]
    ast("body").arr.foreach { node =>
      node("type").str match {
        case "Comment" | "GroupComment" | "ResourceComment" => ()
        case "Junk" =>
          node("annotations").arr.foreach { annotation =>
            val start = annotation("span")("start").num.toInt
            val line = source.take(start).count(_ == '\n') + 1
            errors += Diagnostic(file, s"line $line: ${annotation("code").str}: ${annotation("message").str}")
          }
        case "Message" | "Term" =>
          val id = (if (node("type").str == "Term") "-" else "") + node("id")("name").str
          try {
            if (entries.contains(id)) fail("Duplicate Fluent entry", id)
            if (node("attributes").arr.nonEmpty) fail("Fluent attributes are not supported yet; use separate schema messages", id)
            entries(id) = pattern(node("value"), id)
          } catch { case Problem(error) => errors += error }
        case _ => errors += Diagnostic(file, "Unsupported Fluent resource entry")
      }
    }

    def refs(pattern: FluentPattern): Vector[String] = pattern.parts.flatMap {
      case Reference(id, _) => Vector(id)
      case Select(selector, variants, _) => refs(FluentPattern(Vector(selector))) ++ variants.flatMap(v => refs(v.value))
      case _ => Vector.empty
    }
    def variables(value: FluentPattern): Set[String] = value.parts.flatMap {
      case Variable(name) => Vector(name)
      case Select(selector, variants, _) => variables(FluentPattern(Vector(selector))) ++ variants.flatMap(v => variables(v.value))
      case Reference(id, _) if !id.startsWith("-") => entries.get(id).toVector.flatMap(variables)
      case _ => Vector.empty
    }.toSet
    // Check even unused terms; an invalid resource must never partially activate.
    def checkGraph(id: String, trail: Vector[String]): Unit = {
      if (trail.contains(id)) fail("Cyclic Fluent reference: " + (trail :+ id).mkString(" -> "), id)
      val entry = entries.getOrElse(id, fail("Unknown Fluent reference", id))
      if (trail.size > 64) fail("Fluent reference nesting exceeds 64", id)
      refs(entry).foreach(ref => checkGraph(ref, trail :+ id))
    }
    entries.keys.foreach { id =>
      try checkGraph(id, Vector.empty) catch { case Problem(error) => errors += error }
    }
    val schemaIds = schema.messages.map(_.catalogId).toSet
    entries.keys.filterNot(id => id.startsWith("-") || schemaIds(id)).foreach { id =>
      errors += Diagnostic(file, "Fluent message is absent from the schema (use a -term for private vocabulary)", Some(id))
    }
    val structuralErrors = errors.result()
    if (structuralErrors.nonEmpty) return Left(structuralErrors.distinct)
    val compiled = Map.newBuilder[String, FluentPattern]
    schema.messages.foreach { message =>
      try {
        val args = message.arguments.toMap
        def resolvePattern(value: FluentPattern, env: Map[String, FluentExpression], trail: Vector[String]): FluentPattern =
          FluentPattern(value.parts.map(resolve(_, env, trail)))
        def resolve(value: FluentExpression, env: Map[String, FluentExpression], trail: Vector[String]): FluentExpression = value match {
          case Variable(name) => env.getOrElse(name, fail("Undeclared Fluent variable or missing term argument", message.id, Some(name)))
          case Reference(id, parameters) =>
            if (trail.contains(id) || trail.size > 64) fail("Cyclic or excessively deep Fluent reference: " + id, message.id)
            val target = entries.getOrElse(id, fail(s"Unknown Fluent reference '$id'", message.id))
            if (id.startsWith("-")) {
              val unknown = parameters.keySet -- variables(target)
              if (unknown.nonEmpty) fail(s"Unknown argument to term '$id'", message.id, Some(unknown.toVector.sorted.head))
            }
            val scope = if (id.startsWith("-")) parameters else env
            Pattern(resolvePattern(target, scope, trail :+ id))
          case Select(selector, variants, _) =>
            val selected = resolve(selector, env, trail)
            val numeric = selected match {
              case Number(_) => true
              case Text(_) => false
              case Variable(name) => args(name) match {
                case ArgumentType.IntType | ArgumentType.LongType | ArgumentType.DecimalType => true
                case ArgumentType.StringType | ArgumentType.BooleanType => false
              }
              case _ => fail("Selectors must be variables or literals in this Fluent subset", message.id)
            }
            variants.foreach { variant =>
              if (numeric && !variant.numeric && !Set("zero", "one", "two", "few", "many", "other")(variant.key))
                fail(s"Invalid numeric selector category '${variant.key}'", message.id)
              if (!numeric && variant.numeric) fail(s"Numeric variant '${variant.key}' used with a string/boolean selector", message.id)
            }
            Select(selected, variants.map(v => v.copy(value = resolvePattern(v.value, env, trail))), numeric)
          case other => other
        }
        val root = entries.getOrElse(message.catalogId, fail(s"Missing Fluent message '${message.catalogId}'", message.id))
        compiled += message.id -> resolvePattern(root, args.keys.map(name => name -> Variable(name)).toMap, Vector(message.catalogId))
      } catch { case Problem(error) => errors += error }
    }
    val problems = errors.result().distinct
    if (problems.nonEmpty) Left(problems)
    else Right(LocaleCatalog(file.getName.stripSuffix(".ftl"), Map.empty, Some(compiled.result())))
  }

  private final case class Problem(diagnostic: Diagnostic) extends RuntimeException(diagnostic.toString)
}
