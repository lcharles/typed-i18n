package typed.i18n

class LocaleMatchingSuite extends munit.FunSuite:
  test("matches regions and case while respecting user preference order") {
    assertEquals(LocaleMatching.lookup(Seq("FR-ch", "en"), Seq("en", "fr")), Some("fr"))
    assertEquals(LocaleMatching.lookup(Seq("fr-CH"), Seq("fr", "fr-CH")), Some("fr-CH"))
    assertEquals(LocaleMatching.lookup(Seq("de", "en-GB"), Seq("en", "fr")), Some("en"))
  }

  test("removes extensions and skips unavailable or invalid preferences") {
    assertEquals(LocaleMatching.lookup(Seq("fr-CH-u-nu-latn"), Seq("fr-CH")), Some("fr-CH"))
    assertEquals(LocaleMatching.lookup(Seq("", "bogus!", "fr_CH", "en"), Seq("en")), Some("en"))
    assertEquals(LocaleMatching.lookup(Seq("de", "fr"), Seq("fr-CA", "fr-FR")), None)
    assertEquals(LocaleMatching.lookup(Seq.empty, Seq("en")), None)
    assertEquals(LocaleMatching.lookup(Seq("en"), Seq.empty), None)
  }
