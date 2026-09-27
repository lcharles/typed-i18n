package typed.i18n.browser

import org.scalajs.dom
import scala.scalajs.js
import typed.i18n.LocaleMatching

class BrowserLocaleSuite extends munit.FunSuite:
  test("local storage round trips, saves only explicitly, and clears") {
    val data = scala.collection.mutable.Map.empty[String, String]
    val storage = js.Dynamic.literal(
      getItem = ((key: String) => data.getOrElse(key, null)): js.Function1[String, String],
      setItem = ((key: String, value: String) => { data(key) = value; () }): js.Function2[String, String, Unit],
      removeItem = ((key: String) => { data.remove(key); () }): js.Function1[String, Unit]
    ).asInstanceOf[dom.Storage]
    val preference = LocalePreference.storage("language", () => storage)
    assertEquals(preference.read(), Right(None))
    assertEquals(BrowserLocale.prioritize(Some(preference), Seq("en-US", "fr")), Vector("en-US", "fr"))
    assert(data.isEmpty)
    assertEquals(preference.write("fr"), Right(()))
    assertEquals(BrowserLocale.prioritize(Some(preference), Seq("en-US", "fr")), Vector("fr", "en-US"))
    assertEquals(preference.clear(), Right(()))
    assertEquals(preference.read(), Right(None))
  }

  test("unavailable storage and stale preferences still allow browser matching") {
    val blocked = LocalePreference.storage("language", () => throw IllegalStateException("blocked"))
    assert(blocked.read().isLeft)
    assert(blocked.write("fr").isLeft)
    assert(blocked.clear().isLeft)
    assertEquals(BrowserLocale.prioritize(Some(blocked), Seq("en")), Vector("en"))
    assertEquals(LocaleMatching.lookup(Seq("removed-locale", "fr-CH"), Seq("en", "fr")), Some("fr"))
  }

  test("cookies encode values, use explicit attributes, and clear the same path") {
    var cookieHeader = "unrelated=value"
    var lastAssignment = ""
    val preference = LocalePreference.cookies("language name", 3600, () => cookieHeader, value => {
      lastAssignment = value
      cookieHeader = if value.contains("Max-Age=0;") then "unrelated=value"
        else "unrelated=value; " + value.takeWhile(_ != ';')
    }, () => true)
    assertEquals(preference.write("fr-CH"), Right(()))
    assertEquals(preference.read(), Right(Some("fr-CH")))
    assertEquals(lastAssignment, "language%20name=fr-CH; Path=/; Max-Age=3600; SameSite=Lax; Secure")
    assertEquals(preference.write("x; injected=value"), Right(()))
    assert(lastAssignment.contains("x%3B%20injected%3Dvalue"))
    assertEquals(preference.clear(), Right(()))
    assert(lastAssignment.contains("Path=/; Max-Age=0; SameSite=Lax; Secure"))
    assertEquals(preference.read(), Right(None))
  }

  test("silently blocked cookies and malformed cookie values return errors") {
    val blocked = LocalePreference.cookies("language", 60, () => "", _ => (), () => false)
    assert(blocked.write("fr").isLeft)
    val malformed = LocalePreference.cookies("language", 60, () => "language=%zz", _ => (), () => false)
    assert(malformed.read().isLeft)
    assertEquals(BrowserLocale.prioritize(Some(malformed), Seq("en")), Vector("en"))
  }
