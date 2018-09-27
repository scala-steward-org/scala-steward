package eu.timepit.scalasteward.util

import cats.implicits._
import eu.timepit.scalasteward.github.data.AuthenticatedUser
import org.scalatest.{FunSuite, Matchers}

class uriUtilTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]

  test("withUserInfo") {
    val str = "https://api.github.com/repos/"
    val user = AuthenticatedUser("user", "pass")
    uriUtil.fromStringWithUser[Result](str, user).map(_.toString) shouldBe
      Right("https://user:pass@api.github.com/repos/")
  }
}
