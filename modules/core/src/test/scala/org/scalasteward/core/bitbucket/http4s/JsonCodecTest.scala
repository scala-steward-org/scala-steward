package org.scalasteward.core.bitbucket.http4s

import io.circe.Json
import org.scalatest.{FunSuite, Matchers}
import org.scalasteward.core.vcs.data.PullRequestState

class JsonCodecTest extends FunSuite with Matchers {

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
