package org.scalasteward.core.github.http4s

import cats.Id
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, Headers, MediaType, Request}
import org.scalasteward.core.github.data.AuthenticatedUser
import org.scalatest.{FunSuite, Matchers}

class authenticationTest extends FunSuite with Matchers {
  test("addCredentials") {
    val request = authentication
      .addCredentials(AuthenticatedUser("user", "pass"))
      .apply(Request[Id](headers = Headers(Accept(MediaType.text.plain))))

    request.headers shouldBe
      Headers(Accept(MediaType.text.plain), Authorization(BasicCredentials("user", "pass")))
  }
}
