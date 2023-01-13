package org.scalasteward.core.forge.github

import cats.Id
import munit.FunSuite
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, Headers, MediaType, Request}
import org.scalasteward.core.forge.data.AuthenticatedUser

class authenticationTest extends FunSuite {
  test("addCredentials") {
    val request = authentication
      .addCredentials[Id](AuthenticatedUser("user", "pass"))
      .apply(Request(headers = Headers(Accept(MediaType.text.plain))))

    assertEquals(
      request.headers,
      Headers(Accept(MediaType.text.plain), Authorization(BasicCredentials("user", "pass")))
    )
  }
}
