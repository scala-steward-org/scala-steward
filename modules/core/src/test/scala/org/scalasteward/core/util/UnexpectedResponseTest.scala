package org.scalasteward.core.util

import munit.FunSuite
import org.http4s.*
import org.typelevel.ci.CIString

class UnexpectedResponseTest extends FunSuite {
  test("getMessage") {
    val unexpected = UnexpectedResponse(
      Uri.unsafeFromString("https://api.github.com/repos/foo/bar/pulls"),
      Method.POST,
      Headers(
        Header.Raw(CIString("access-control-allow-origin"), "*"),
        Header.Raw(CIString("content-type"), "application/json; charset=utf-8")
      ),
      Status.Forbidden,
      """{ message: "nope" }"""
    )
    val expected =
      """|uri: https://api.github.com/repos/foo/bar/pulls
         |method: POST
         |status: 403 Forbidden
         |headers:
         |  access-control-allow-origin: *
         |  content-type: application/json; charset=utf-8
         |body: { message: "nope" }""".stripMargin
    assertEquals(unexpected.getMessage, expected)
  }
}
