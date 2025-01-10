package org.scalasteward.core.forge.data

import io.circe.parser
import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.forge.data.PullRequestState.Open
import scala.io.Source

class PullRequestOutTest extends FunSuite {
  test("decode") {
    val expected =
      List(
        PullRequestOut(
          uri"https://github.com/octocat/Hello-World/pull/1347",
          Open,
          PullRequestNumber(1347),
          "new-feature"
        )
      )

    val input = Source.fromResource("list-pull-requests.json").mkString
    assertEquals(parser.decode[List[PullRequestOut]](input), Right(expected))
  }
}
