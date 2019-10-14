package org.scalasteward.core.util

import org.http4s.Uri.UserInfo
import org.http4s.syntax.literals._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class uriTest extends AnyFunSuite with Matchers {

  test("withUserInfo") {
    val url = uri"https://api.github.com/repos/"
    uri.withUserInfo.set(UserInfo("user", Some("pass")))(url).toString shouldBe
      "https://user:pass@api.github.com/repos/"
  }
}
