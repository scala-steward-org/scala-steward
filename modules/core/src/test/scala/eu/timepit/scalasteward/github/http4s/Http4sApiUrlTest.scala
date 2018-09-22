package eu.timepit.scalasteward.github.http4s

import cats.implicits._
import eu.timepit.scalasteward.github.GitHubRepo
import org.scalatest.{FunSuite, Matchers}

class Http4sApiUrlTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]
  val repo = GitHubRepo("fthomas", "refined")

  test("forks") {
    Http4sApiUrl.forks[Result](repo).map(_.toString) shouldBe
      Right("https://api.github.com/repos/fthomas/refined/forks")
  }
}
