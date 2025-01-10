package org.scalasteward.core.forge.data

import io.circe.Decoder
import io.circe.syntax.*
import munit.FunSuite
import org.scalasteward.core.forge.data.PullRequestState.{Closed, Open}

class PullRequestStateTest extends FunSuite {
  def roundTrip(state: PullRequestState): Unit =
    assertEquals(Decoder[PullRequestState].decodeJson(state.asJson), Right(state))

  test("round-trip") {
    roundTrip(Open)
    roundTrip(Closed)
  }
}
