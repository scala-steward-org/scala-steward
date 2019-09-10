package org.scalasteward.core.github.http4s

import cats.Id
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, Headers, MediaType, Request}
import org.scalasteward.core.vcs.data.AuthenticatedUser
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite

class authenticationTest extends AnyFunSuite with Matchers {
  test("addCredentials") {
    val request = authentication
      .addCredentials(AuthenticatedUser("user", "pass"))
      .apply(Request[Id](headers = Headers.of(Accept(MediaType.text.plain))))

    request.headers shouldBe
      Headers.of(Accept(MediaType.text.plain), Authorization(BasicCredentials("user", "pass")))
  }
}
