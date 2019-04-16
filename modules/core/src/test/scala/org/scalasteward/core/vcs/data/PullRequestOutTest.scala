package org.scalasteward.core.vcs.data

import io.circe.parser
import org.http4s.Uri
import org.scalasteward.core.vcs.data.PullRequestState.Open
import org.scalatest.{FunSuite, Matchers}
import scala.io.Source

class PullRequestOutTest extends FunSuite with Matchers {
  test("decode") {
    val expected =
      List(
        PullRequestOut(
          Uri.unsafeFromString("https://github.com/octocat/Hello-World/pull/1347"),
          Open,
          "new-feature"
        )
      )

    val input = Source.fromResource("list-pull-requests.json").mkString
    parser.decode[List[PullRequestOut]](input) shouldBe Right(expected)
  }
}
