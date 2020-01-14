package org.scalasteward.core.github.data

import org.scalatest.funsuite.AnyFunSuite
import org.scalasteward.core.vcs.data.UserOut
import org.scalasteward.core.github.data.GitHubResponse
import io.circe.parser
import scala.io.Source
import org.http4s.implicits._
import org.scalasteward.core.git.Branch
import org.scalatest.matchers.should.Matchers

class Http4sGitHubApiAlgTest extends AnyFunSuite with Matchers {

  test("decode") {
    val parent =
      GitHubResponse(
        "base.g8",
        UserOut("ChristopherDavenport"),
        None,
        uri"https://github.com/ChristopherDavenport/base.g8.git",
        Branch("master")
      )

    val fork =
      GitHubResponse(
        "base.g8-1",
        UserOut("scala-steward"),
        Some(parent),
        uri"https://github.com/scala-steward/base.g8-1.git",
        Branch("master")
      )
    val input = Source.fromResource("create-fork.json").mkString
    parser.decode[GitHubResponse](input) shouldBe Right(fork)
  }
}
