package org.scalasteward.core.util

import cats.syntax.all.*
import munit.CatsEffectSuite
import org.http4s.{Headers, HttpApp, Response, Status}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.syntax.all.*
import org.scalasteward.core.mock.*
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.util.UrlChecker.UrlValidationResult

class UrlCheckerTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val baseUrl = uri"https://github.com/scala-steward-org"
  private val redirectUrl = baseUrl / "finished-redirect"
  private val anotherRedirectUrl = baseUrl / "yet-another-redirect"

  private val state =
    MockState.empty.copy(clientResponses = GitHubAuth.api(List.empty) <+> HttpApp {
      case HEAD -> Root / "scala-steward-org"               => Ok()
      case HEAD -> Root / "scala-steward-org" / "wrong-uri" => NotFound()
      case HEAD -> Root / "scala-steward-org" / "single-redirect" =>
        Response[MockEff](
          status = Status.MovedPermanently,
          headers = Headers(Location(redirectUrl))
        ).pure[MockEff]
      case HEAD -> Root / "scala-steward-org" / "finished-redirect" => Ok()
      case HEAD -> Root / "scala-steward-org" / "yet-another-redirect" =>
        Response[MockEff](
          status = Status.MovedPermanently,
          headers = Headers(Location(anotherRedirectUrl))
        ).pure[MockEff]
      case _ =>
        ServiceUnavailable()
    })

  test("An URL exists") {
    val url = baseUrl
    val check = urlChecker.validate(url).runA(state)

    assertIOBoolean(check.map(_.exists))
  }

  test("An URL does not exist") {
    val url = baseUrl / "wrong-uri"
    val check = urlChecker.validate(url).runA(state)

    assertIOBoolean(check.map(_.notExists))
  }

  test("An URL redirects to another existing URL") {
    val httpUrl = uri"https://github.com/scala-steward-org/single-redirect"
    val check = urlChecker.validate(httpUrl).runA(state)
    val anticipatedResult = UrlValidationResult.RedirectTo(redirectUrl)

    assertIO(check, anticipatedResult)
  }

  // basically, we prohibit the infinite loop of traversing redirect URLs
  test("An URL redirects to another redirecting URL") {
    val httpUrl = uri"https://github.com/scala-steward-org/yet-another-redirect"
    val check = urlChecker.validate(httpUrl).runA(state)
    val anticipatedResult = UrlValidationResult.NotExists

    assertIO(check, anticipatedResult)
  }
}
