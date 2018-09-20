package eu.timepit.scalasteward.gh

import io.circe.parser
import org.scalatest.{FunSuite, Matchers}
import scala.io.Source

class GitHubRepoResponseTest extends FunSuite with Matchers {
  test("decode[GitHubResponse]") {
    val input = Source.fromResource("fork_response.json").mkString
    parser.decode[GitHubRepoResponse](input) shouldBe Right(
      GitHubRepoResponse("base.g8-1", Some(GitHubRepoResponse("base.g8", None, "master")), "master")
    )
  }
}
