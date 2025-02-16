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
  private val redirectUrl = baseUrl / "scala-steward"

  private val state =
    MockState.empty.copy(clientResponses = GitHubAuth.api(List.empty) <+> HttpApp {
      case HEAD -> Root / "scala-steward-org"               => Ok()
      case HEAD -> Root / "scala-steward-org" / "wrong-uri" => NotFound()
      case HEAD -> Root / "scala-steward-org" / "redirect-uri" =>
        Response[MockEff](
          status = Status.MovedPermanently,
          headers = Headers(Location(redirectUrl))
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

  test("An URL redirects to another URL") {
    val httpUrl = uri"https://github.com/scala-steward-org/redirect-uri"
    val check = urlChecker.validate(httpUrl).runA(state)
    val anticipatedResult = UrlValidationResult.RedirectTo(redirectUrl)

    assertIO(check, anticipatedResult)
  }
}
