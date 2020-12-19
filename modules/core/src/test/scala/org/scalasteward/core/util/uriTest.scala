package org.scalasteward.core.util

import munit.FunSuite
import org.http4s.Uri.UserInfo
import org.http4s.syntax.literals._

class uriTest extends FunSuite {
  test("withUserInfo") {
    val url = uri"https://api.github.com/repos/"
    assertEquals(
      uri.withUserInfo.set(UserInfo("user", Some("pass")))(url).toString,
      "https://user:pass@api.github.com/repos/"
    )
  }
}
