package eu.timepit.scalasteward.util

import cats.implicits._
import org.http4s.Uri
import org.scalatest.{FunSuite, Matchers}

class uriTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]

  test("fromString") {
    val str = "https://api.github.com/repos/"
    uri.fromString[Result](str).map(_.toString) shouldBe
      Right("https://api.github.com/repos/")
  }

  test("withUserInfo") {
    val url = Uri.uri("https://api.github.com/repos/")
    uri.withUserInfo(url, "user:pass").toString shouldBe
      "https://user:pass@api.github.com/repos/"
  }
}
