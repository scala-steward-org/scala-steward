package eu.timepit.scalasteward.github

import io.circe.parser
import org.scalatest.{FunSuite, Matchers}
import scala.io.Source

class GitHubRepoOutTest extends FunSuite with Matchers {
  test("decode[GitHubResponse]") {
    val input = Source.fromResource("fork_response.json").mkString
    parser.decode[GitHubRepoOut](input) shouldBe
      Right(
        GitHubRepoOut(
          "base.g8-1",
          GitHubUserOut("scala-steward"),
          Some(GitHubRepoOut("base.g8", GitHubUserOut("ChristopherDavenport"), None, "master")),
          "master"
        )
      )
  }
}
