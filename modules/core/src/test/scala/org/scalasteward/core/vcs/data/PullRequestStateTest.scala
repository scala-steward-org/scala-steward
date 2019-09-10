package org.scalasteward.core.vcs.data

import io.circe.Decoder
import io.circe.syntax._
import org.scalasteward.core.vcs.data.PullRequestState.{Closed, Open}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{Assertion, Matchers}

class PullRequestStateTest extends AnyFunSuite with Matchers {
  def roundTrip(state: PullRequestState): Assertion =
    Decoder[PullRequestState].decodeJson(state.asJson) shouldBe Right(state)

  test("round-trip") {
    roundTrip(Open)
    roundTrip(Closed)
  }
}
