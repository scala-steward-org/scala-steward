package eu.timepit.scalasteward.util

import cats.implicits._
import eu.timepit.scalasteward.github.data.AuthenticatedUser
import org.http4s.Uri
import org.scalatest.{FunSuite, Matchers}

class uriUtilTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]

  test("fromString") {
    val str = "https://api.github.com/repos/"
    uriUtil.fromString[Result](str).map(_.toString) shouldBe
      Right("https://api.github.com/repos/")
  }

  test("withUserInfo") {
    val url = Uri.uri("https://api.github.com/repos/")
    val user = AuthenticatedUser("user", "pass")
    uriUtil.withUserInfo(url, user).toString shouldBe
      "https://user:pass@api.github.com/repos/"
  }
}
