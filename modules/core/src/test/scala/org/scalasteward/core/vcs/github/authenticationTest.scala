package org.scalasteward.core.vcs.github

import cats.Id
import munit.FunSuite
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, Headers, MediaType, Request}
import org.scalasteward.core.vcs.data.AuthenticatedUser

class authenticationTest extends FunSuite {
  test("addCredentials") {
    val request = authentication
      .addCredentials[Id](AuthenticatedUser("user", "pass"))
      .apply(Request(headers = Headers.of(Accept(MediaType.text.plain))))

    assertEquals(
      request.headers,
      Headers.of(Accept(MediaType.text.plain), Authorization(BasicCredentials("user", "pass")))
    )
  }
}
