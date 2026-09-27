# typed-i18n

Statically typed translations for Scala 3, JVM, Scala.js, and Laminar applications.

```scala
val strings = Strings.forLocale(Strings.Locale.Fr)
strings.common.save
strings.account.welcome("Lucas")
strings.survey.answerCount(12)
```

Every call returns an ordinary `String`. Unknown messages, misspelled names,
missing/extra arguments, and wrong argument types are Scala compilation errors.
There are no macros, structural types, `scala.Dynamic`, or reactive translation
values.

Message shape lives in a JSON schema. Translators author content in **Fluent
catalogs**. The sbt plugin validates and compiles catalogs into the application.
There is no catalog fetch, parsing, or validation at application startup.

## Start on another machine

Install **JDK 21**, **sbt**, and **Node.js 24** (Node 22 also supports the build
parser). The browser demo additionally needs **Python 3**.

```bash
git clone https://github.com/lcharles/typed-i18n.git
cd typed-i18n
sbt check
./scripts/run-laminar-demo.sh
```

Open <http://127.0.0.1:8080/index.html>. Pass another port to the launcher if
needed, e.g. `./scripts/run-laminar-demo.sh 9000`. Stop with Ctrl-C.
The launcher handles local test-artifact installation and Scala.js linking;
there is no separate manual `publishLocal` or npm-install step.

The demo displays zero, one, and twelve answers using English/French Fluent
variants. Language links save your preference and reload the page.
Its HTML loads the generated `main.js` directly. A conventional `App.main`
creates `Strings` synchronously and mounts Laminar with
`renderOnDomContentLoaded`.

## Modules and versions

- `modules/runtime/shared`: argument types, optional JSON loading, rendering,
  locale matching, and exact CLDR numeric operands.
- `modules/runtime/jvm`: filesystem JSON loading.
- `modules/runtime/js`: browser language and preference helpers.
- `modules/sbt-plugin`: the AutoPlugin, Fluent compiler, and generator.
- `modules/sbt-plugin/src/sbt-test/typed-i18n`: real consumer builds, including
  the runnable Laminar app. They are part of this repository.

Pinned versions: sbt 1.12.14; Scala 3.9.0 for runtime/consumers; Scala 2.12.21
for the sbt 1.x plugin; Scala.js 1.22.0; sbt-crossproject 1.4.0; ujson 4.4.3;
MUnit 1.2.4; scalajs-dom 2.8.1; Laminar 17.2.1 (demo only).

The plugin packages Mozilla's official `@fluent/syntax` 0.19.0 parser and invokes
it with Node **at build time**. It uses ICU4J 78.3 / CLDR 48 data to generate
plural arithmetic. Neither Node, the Fluent parser, nor ICU is an application
runtime dependency. Parser provenance and licenses are included under
`modules/sbt-plugin/src/main/resources/typed-i18n`.

## Schema

Default: `src/main/i18n/messages.schema.json`.

```json
{
  "package": "example.i18n",
  "messages": {
    "common.save": { "arguments": {} },
    "account.welcome": { "arguments": { "userName": "string" } },
    "survey.answerCount": {
      "fluentId": "survey-answer-count",
      "arguments": { "count": "int" }
    }
  }
}
```

Supported types: `string` → `String`, `int` → `Int`, `long` → `Long`,
`decimal` → `BigDecimal`, `boolean` → `Boolean`.
Method arguments are generated in alphabetical name order; named Scala arguments
are supported.

Dotted IDs become nested typed accessors. Names must be valid Scala identifiers;
reserved words and namespace/class collisions are rejected. IDs need at least
one namespace. By default, a Fluent ID replaces dots with hyphens, preserving
case: `account.welcome` maps to `account-welcome`. Set `fluentId` explicitly
when desired. Invalid or colliding Fluent mappings fail validation.

## Fluent catalogs

Default directory: `src/main/i18n/catalogs`. One file per locale, named
`en.ftl`, `fr.ftl`, `fr-CH.ftl`, etc. The filename supplies the locale.

```ftl
# en.ftl
-brand = Example

common-save = Save
account-welcome = Welcome, { $userName }, to { -brand }!

survey-answer-count =
    { $count ->
        [0] No answers yet
        [one] { $count } answer
       *[other] { $count } answers
    }
```

Each locale chooses its own branches. CLDR categories (`zero`, `one`, `two`,
`few`, `many`, `other`) are locale-specific, not heuristics such as `count == 1`.
An explicit numeric key like `[0]` takes precedence over a category. Every
selector needs one starred default branch.

String selectors and parameterized terms are supported:

```ftl
-address = { $style ->
    [short] friend
   *[long] dear friend
    }

account-welcome = Hello, { $userName }, { -address(style: "long") }!
```

The supported subset includes comments, multiline patterns, variables, string
and number literals (including Fluent string escapes), nested selectors,
message references, private terms, and named literal term arguments. All
used term parameters must be supplied; unknown term arguments are errors.
Message references must refer to schema-declared messages. Terms have their
own parameter scope and do not implicitly capture application variables.

**This is a Fluent compiler subset, not a complete Fluent runtime.**
Functions (`NUMBER`/`DATETIME`/custom functions), attributes, term-attribute
selectors, and selectors over reference results are rejected explicitly.
Number interpolation currently uses plain decimal text, without localized
grouping or decimal separators; automatic bidi isolation is not yet implemented.
Do not treat this milestone as full Fluent rendering conformance.

Plural selection preserves `Long` precision and `BigDecimal` scale on both JVM
and Scala.js: `BigDecimal("1.0")` has one visible fraction digit, unlike an
integer `1`. Compact-number exponents are not supported. Locale languages must
exist in the pinned CLDR plural data; unsupported locales fail the build.

Fluent translations may omit schema arguments they do not need. Every variable
they do use, including through references and terms, must be declared and
compatible with its selector. Required messages, unknown messages, syntax
errors, duplicate entries/variants/locales, missing references, reference cycles,
and unsupported features fail the build. Diagnostics identify the file and
message/argument where applicable; parser errors include line numbers.

## Consumer configuration

The Scripted fixtures are usable examples before artifacts are published.
A published consumer would add to `project/plugins.sbt`:

```scala
addSbtPlugin("com.quidux" % "sbt-typed-i18n" % "<version>")
```

Then:

```scala
import com.quidux.typedi18n.sbt.TypedI18nPlugin

lazy val app = project
  .enablePlugins(TypedI18nPlugin)
  .settings(
    scalaVersion := "3.9.0",
    libraryDependencies +=
      "com.quidux" %% "typed-i18n-runtime" % "<version>"
  )
```

For Scala.js, also enable `ScalaJSPlugin` and use `%%%` for the runtime dependency.

Plugin keys:

- `typedI18nSchema`: schema file.
- `typedI18nCatalogDirectory`: catalog directory.
- `typedI18nBundleCatalogs`: `true` by default; Fluent requires bundling.
- `typedI18nValidate`: validate every `.ftl` and `.json` catalog.
- `typedI18nGenerate`: validate and generate `Strings.scala`.

Generation participates in `Compile / sourceGenerators`, so normal compilation
is strict. Current inputs are read on each task invocation. Output is
deterministic and files are rewritten only when their content changes.
Changing translations requires rebuilding. All configured locales are bundled.

Generated source is readable under
`Compile / sourceManaged / typed-i18n / Strings.scala`.
Zero-argument messages are parameterless `def`s.

`Strings.Locale.Fr` and `Strings.Locale.FrCh` are generated from `fr` and
`fr-CH`. `Strings.availableLocales` lists the supported locales.
`Strings.forLocale` returns the **same immutable instance** for a given locale.
The instance and namespace wrappers are initialized once per locale; repeated
calls only return the reference. Optional external-loading schema metadata is
lazy and unused by the bundled path. Parameterized messages select branches
and construct their output when called.

## Browser selection and persistence

```scala
import example.i18n.Strings
import typed.i18n.browser.{BrowserLocale, LocalePreference}

val preference = LocalePreference.localStorage()
// Alternatively: LocalePreference.cookie()

val locale = Strings.localeFor(
  BrowserLocale.preferredLanguages(Some(preference)),
  fallback = Strings.Locale.En
)
val strings = Strings.forLocale(locale)

// Explicit user choice; handle a Left if saving fails:
preference.write(Strings.Locale.Fr.tag)
// preference.clear() removes the preference.
```

Selection tries the saved preference, then browser languages, then the explicit
fallback. Case-insensitive matching progressively shortens tags: `fr-CH` can
match `fr`. `fr` does not arbitrarily choose `fr-FR`. Unavailable preferences
and storage read errors fall through. Detection alone never saves a preference.

Cookies are host-only, `Path=/`, `SameSite=Lax`, and `Secure` on HTTPS, with
a configurable lifetime (one year by default). Local storage uses the current
origin. Writes return `Either[PreferenceError, Unit]` and are verified.

Translations intentionally return ordinary strings, not Laminar `Signal`s.
Changing languages means selecting another `Strings` instance and remounting
or reloading the relevant UI. The library has no Laminar dependency.

## Optional JSON compatibility

The original JSON format still works for simple named substitution:

```json
{
  "locale": "fr",
  "messages": {
    "common.save": "Enregistrer",
    "account.welcome": "Bienvenue, {userName}",
    "survey.answerCount": "{count} réponses"
  }
}
```

JSON and Fluent may coexist for different locales; duplicate locale definitions
are rejected. JSON retains its original strict placeholder checks (including
missing placeholders), whereas Fluent permits unused schema arguments.

For externally supplied JSON, use
`CatalogLoader.fromJson(Strings.schema, json)` or JVM-only
`JvmCatalogLoader.load(Strings.schema, path)`, then `Strings.fromCatalog(catalog)`.
These return `Either` and never activate a partially valid catalog.
`typedI18nBundleCatalogs := false` supports JSON-only external deployments.
Runtime loading of Fluent is outside this milestone.

## Tests and direction

```bash
sbt check
```

Runs JVM and Node-backed Scala.js unit tests, plugin tests, local test-artifact
installation, and all six Scripted builds: `basic`, `fluent`, `laminar-app`,
`unknown-message`, `invalid-catalog`, `invalid-fluent`.
No artifacts are published remotely.

The Fluent fixture executes the same assertions on JVM and Scala.js: English,
French, Russian, Arabic and Polish plurals; exact-number overrides; terms;
string selectors; decimal operands; exact Long interpolation; and singleton
identity. Plugin tests compare generated-rule semantics to ICU across every
available cardinal-rule locale. Negative fixtures assert build failure.

Next: localized number/date/currency formatting, bidi isolation, further Fluent
features and conformance tests. MessageFormat 2 remains a possible additional
frontend behind the same typed API. Rich text, production hot reloading,
remote catalog fetching, and reactive translation values are out of scope.

## License

Project code is MIT. The vendored Fluent parser is Apache-2.0, with its upstream
notice preserved. CLDR-derived plural data is covered by the included Unicode
license; generated Fluent source carries that notice, and the runtime artifact
includes it under `META-INF/typed-i18n-UNICODE-LICENSE.txt`.
