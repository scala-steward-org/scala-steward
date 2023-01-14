package org.scalasteward.core.forge

import cats.Id
import munit.FunSuite
import org.http4s.headers.{Accept, Authorization}
import org.http4s.syntax.all._
import org.http4s.{BasicCredentials, Headers, MediaType, Request}
import org.scalasteward.core.forge.ForgeType.GitHub
import org.scalasteward.core.forge.data.AuthenticatedUser
import org.scalasteward.core.mock.MockConfig

class ForgeSelectionTest extends FunSuite {
  test("authenticate") {
    val obtained = ForgeSelection
      .authenticate[Id](GitHub, AuthenticatedUser("user", "pass"))
      .apply(Request(headers = Headers(Accept(MediaType.text.plain))))
      .headers
    val expected =
      Headers(Accept(MediaType.text.plain), Authorization(BasicCredentials("user", "pass")))
    assertEquals(obtained, expected)
  }

  test("authenticateIfApiHost") {
    val forgeCfg = MockConfig.config.forgeCfg
    val auth = ForgeSelection.authenticateIfApiHost[Id](forgeCfg, AuthenticatedUser("user", "pass"))

    val obtained1 = auth.apply(Request(uri = uri"http://example.com/foo/bar")).headers
    val expected1 = Headers(Authorization(BasicCredentials("user", "pass")))
    assertEquals(obtained1, expected1)

    val obtained2 = auth.apply(Request(uri = uri"http://acme.org/foo/bar")).headers
    val expected2 = Headers()
    assertEquals(obtained2, expected2)
  }
}
