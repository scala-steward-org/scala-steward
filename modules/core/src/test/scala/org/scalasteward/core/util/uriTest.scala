package org.scalasteward.core.util

import org.http4s.Http4sLiteralSyntax
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class uriTest extends AnyFunSuite with Matchers {

  test("withUserInfo") {
    val url = uri"https://api.github.com/repos/"
    uri.withUserInfo.set("user:pass")(url).toString shouldBe
      "https://user:pass@api.github.com/repos/"
  }
}
