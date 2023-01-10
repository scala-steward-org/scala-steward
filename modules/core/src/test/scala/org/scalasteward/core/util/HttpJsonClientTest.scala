package org.scalasteward.core.util

import cats.syntax.all._
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Link, LinkValue}
import org.http4s.syntax.all._
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEff, MockState}

class HttpJsonClientTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  test("getAll") {
    val url1 = uri"https://example.org/1"
    val url2 = uri"https://example.org/2"
    val state = MockState.empty.copy(clientResponses = HttpApp {
      case GET -> Root / "1" => Ok("1", Link(LinkValue(url2, Some("next"))))
      case GET -> Root / "2" => Ok("2", Link(LinkValue(url1, Some("prev"))))
      case _                 => NotFound()
    })
    val obtained = httpJsonClient.getAll[Int](url1, _.pure[MockEff]).runA(state)
    assertIO(obtained, List(2, 1))
  }
}
