package org.scalasteward.core.bitbucket.http4s

import io.circe.Json
import org.scalasteward.core.vcs.data.PullRequestState
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonCodecTest extends AnyFunSuite with Matchers {

  test("PullRequestStatus decoding of expected values") {

    val mapping = Map(
      "OPEN" -> PullRequestState.Open,
      "MERGED" -> PullRequestState.Closed,
      "SUPERSEDED" -> PullRequestState.Closed,
      "DECLINED" -> PullRequestState.Closed
    )

    mapping.foreach {
      case (string, state) =>
        json.pullRequestStateDecoder.decodeJson(Json.fromString(string)) shouldBe Right(state)
    }

  }
}
