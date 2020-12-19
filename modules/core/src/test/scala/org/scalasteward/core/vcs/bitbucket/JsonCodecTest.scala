package org.scalasteward.core.vcs.bitbucket

import io.circe.Json
import munit.FunSuite
import org.scalasteward.core.vcs.data.PullRequestState

class JsonCodecTest extends FunSuite {
  test("PullRequestStatus decoding of expected values") {
    val mapping = Map(
      "OPEN" -> PullRequestState.Open,
      "MERGED" -> PullRequestState.Closed,
      "SUPERSEDED" -> PullRequestState.Closed,
      "DECLINED" -> PullRequestState.Closed
    )

    mapping.foreach { case (string, state) =>
      assertEquals(json.pullRequestStateDecoder.decodeJson(Json.fromString(string)), Right(state))
    }
  }
}
