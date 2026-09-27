package example

import com.raquo.laminar.api.L.{*, given}
import example.i18n.Strings
import org.scalajs.dom
import typed.i18n.browser.{BrowserLocale, LocalePreference}

object App:
  def main(args: Array[String]): Unit =
    lazy val appContainer = dom.document.querySelector("#appContainer")
    val preference = LocalePreference.localStorage()
    // Swap in LocalePreference.cookie() to persist the choice in a cookie.
    val url = new dom.URL(dom.window.location.href)
    val chosen = Option(url.searchParams.get("locale"))
      .flatMap(tag => Strings.availableLocales.find(_.tag.equalsIgnoreCase(tag)))

    chosen.foreach { locale =>
      preference.write(locale.tag).left.foreach(error => dom.console.warn(error.toString))
    }
    // A language link records a choice; subsequent reloads use the saved preference.
    if url.searchParams.has("locale") then
      url.searchParams.delete("locale")
      dom.window.history.replaceState(null, "", url.toString)

    val locale = Strings.localeFor(
      chosen.map(_.tag).toVector ++ BrowserLocale.preferredLanguages(Some(preference)),
      fallback = Strings.Locale.Fr
    )
    val strings = Strings.forLocale(locale)
    val appElement = view(strings)
    dom.document.documentElement.setAttribute("lang", locale.tag)
    renderOnDomContentLoaded(appContainer, appElement)

  def view(strings: Strings): HtmlElement =
    mainTag(
      h1(strings.account.welcome("Lucas")),
      p(strings.survey.answerCount(0)),
      p(strings.survey.answerCount(1)),
      p(strings.survey.answerCount(12)),
      button(strings.common.save)
    )
