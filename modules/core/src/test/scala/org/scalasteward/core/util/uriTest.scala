package org.scalasteward.core.util

import munit.FunSuite
import org.http4s.Uri.UserInfo
import org.http4s.syntax.literals.*
import org.scalasteward.core.util

class uriTest extends FunSuite {
  test("withUserInfo") {
    val url = uri"https://api.github.com/repos/"
    val obtained = util.uri.withUserInfo.replace(UserInfo("user", Some("pass")))(url).toString
    val expected = "https://user:pass@api.github.com/repos/"
    assertEquals(obtained, expected)
  }
}
