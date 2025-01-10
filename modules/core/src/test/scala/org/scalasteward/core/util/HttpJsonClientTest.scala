package org.scalasteward.core.util

import cats.syntax.all.*
import munit.CatsEffectSuite
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Link, LinkValue}
import org.http4s.syntax.all.*
import org.http4s.{HttpApp, HttpVersion, Status}
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockState}

class HttpJsonClientTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  test("getAll") {
    val url1 = uri"https://example.org/1"
    val url2 = uri"https://example.org/2"
    val state = MockState.empty.copy(clientResponses = HttpApp {
      case GET -> Root / "1" => Ok("1", Link(LinkValue(url2, Some("next"))))
      case GET -> Root / "2" => Ok("2", Link(LinkValue(url1, Some("prev"))))
      case _                 => NotFound()
    })
    val obtained = httpJsonClient.getAll[Int](url1, _.pure[MockEff]).compile.toList.runA(state)
    assertIO(obtained, List(1, 2))
  }

  test("get with malformed JSON") {
    val state = MockState.empty.copy(clientResponses = HttpApp {
      case GET -> Root => Ok(" \"1 ")
      case _           => NotFound()
    })
    val obtained = httpJsonClient
      .get[Int](uri"https://example.org", _.pure[MockEff])
      .runA(state)
      .attempt
      .map(_.leftMap(_.getMessage))
    val expected = Left("""uri: https://example.org
                          |method: GET
                          |message: Malformed message body: Invalid JSON
                          |body:  "1 """.stripMargin)
    assertIO(obtained, expected)
  }

  test("get with invalid JSON") {
    val state = MockState.empty.copy(clientResponses = HttpApp {
      case GET -> Root => Ok(" 1 ")
      case _           => NotFound()
    })
    val obtained = httpJsonClient
      .get[String](uri"https://example.org", _.pure[MockEff])
      .runA(state)
      .attemptNarrow[DecodeFailureWithContext]
      .map(_.leftMap(_.toHttpResponse(HttpVersion.`HTTP/1.1`).status))
    val expected = Left(Status.UnprocessableEntity)
    assertIO(obtained, expected)
  }
}
