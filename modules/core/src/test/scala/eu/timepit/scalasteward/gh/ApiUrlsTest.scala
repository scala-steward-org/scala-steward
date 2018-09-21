package eu.timepit.scalasteward.gh

import org.scalatest.{FunSuite, Matchers}
import cats.implicits._

class ApiUrlsTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]

  test("forks") {
    ApiUrls.forks[Result](GitHubRepo("fthomas", "refined")).map(_.toString) shouldBe
      Right("https://api.github.com/repos/fthomas/refined/forks")
  }
}
