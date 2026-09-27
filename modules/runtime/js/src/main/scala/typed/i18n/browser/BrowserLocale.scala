package typed.i18n.browser

import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

/** Browser access happens only when a method is called, never at module startup. */
object BrowserLocale:
  def preferredLanguages(preference: Option[LocalePreference] = None): Vector[String] =
    val languages = dom.window.navigator.languages.toVector
    prioritize(
      preference,
      if languages.nonEmpty then languages else Vector(dom.window.navigator.language)
    )

  private[i18n] def prioritize(
      preference: Option[LocalePreference],
      languages: Seq[String]
  ): Vector[String] =
    (preference.flatMap(_.read().toOption.flatten).toVector ++ languages)
      .filter(_.nonEmpty).distinct

final case class PreferenceError(operation: String, detail: String)

/** Saving is explicit. Storage failures are values, so they need not prevent rendering. */
trait LocalePreference:
  def read(): Either[PreferenceError, Option[String]]
  def write(locale: String): Either[PreferenceError, Unit]
  def clear(): Either[PreferenceError, Unit]

object LocalePreference:
  def localStorage(key: String = "typed-i18n.locale"): LocalePreference =
    storage(key, () => dom.window.localStorage)

  /** Host-only cookie, Path=/, SameSite=Lax; Secure on HTTPS. */
  def cookie(
      key: String = "typed-i18n.locale",
      maxAgeSeconds: Int = 31536000
  ): LocalePreference =
    cookies(key, maxAgeSeconds, () => dom.document.cookie,
      value => dom.document.cookie = value, () => dom.window.location.protocol == "https:")

  private[i18n] def storage(key: String, getStorage: () => dom.Storage): LocalePreference =
    guarded(
      () => Option(getStorage().getItem(key)),
      value => getStorage().setItem(key, value),
      () => getStorage().removeItem(key)
    )

  private[i18n] def cookies(
      key: String,
      maxAgeSeconds: Int,
      getCookie: () => String,
      setCookie: String => Unit,
      secure: () => Boolean
  ): LocalePreference =
    require(key.nonEmpty, "Cookie key must not be empty")
    require(maxAgeSeconds > 0, "Cookie lifetime must be positive")
    val encodedKey = js.URIUtils.encodeURIComponent(key)
    def assignment(value: String, age: Int): String =
      s"$encodedKey=${js.URIUtils.encodeURIComponent(value)}; Path=/; Max-Age=$age; SameSite=Lax" +
        (if secure() then "; Secure" else "")
    guarded(
      () => getCookie().split(";").iterator.map(_.trim)
        .find(_.startsWith(encodedKey + "="))
        .map(value => js.URIUtils.decodeURIComponent(value.substring(encodedKey.length + 1))),
      value => setCookie(assignment(value, maxAgeSeconds)),
      () => setCookie(assignment("", 0))
    )

  private def guarded(
      get: () => Option[String],
      put: String => Unit,
      remove: () => Unit
  ): LocalePreference = new LocalePreference:
    private def attempt[A](operation: String)(body: => A): Either[PreferenceError, A] =
      try Right(body)
      catch
        case NonFatal(error) => Left(PreferenceError(operation, error.toString))

    def read(): Either[PreferenceError, Option[String]] = attempt("read")(get())

    def write(locale: String): Either[PreferenceError, Unit] = attempt("write") {
      put(locale)
      if !get().contains(locale) then
        throw IllegalStateException("Browser did not persist the locale preference")
    }

    def clear(): Either[PreferenceError, Unit] = attempt("clear") {
      remove()
      if get().nonEmpty then
        throw IllegalStateException("Browser did not remove the locale preference")
    }
