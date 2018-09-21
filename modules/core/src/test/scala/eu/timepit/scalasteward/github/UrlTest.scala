package eu.timepit.scalasteward.github

import cats.implicits._
import org.scalatest.{FunSuite, Matchers}

class UrlTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]
  val repo = GitHubRepo("fthomas", "refined")

  test("forks") {
    Url.forks[Result](repo).map(_.toString) shouldBe
      Right("https://api.github.com/repos/fthomas/refined/forks")
  }
}
