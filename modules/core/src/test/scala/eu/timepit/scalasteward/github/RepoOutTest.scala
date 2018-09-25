package eu.timepit.scalasteward.github

import io.circe.parser
import org.scalatest.{FunSuite, Matchers}
import scala.io.Source

class RepoOutTest extends FunSuite with Matchers {
  test("decode[GitHubRepoOut]") {
    val input = Source.fromResource("fork_response.json").mkString
    parser.decode[RepoOut](input) shouldBe
      Right(
        RepoOut(
          "base.g8-1",
          UserOut("scala-steward"),
          Some(RepoOut("base.g8", UserOut("ChristopherDavenport"), None, "master")),
          "master"
        )
      )
  }
}
