package org.scalasteward.core.nurture

import io.circe.syntax.*
import munit.FunSuite
import org.scalasteward.core.data.NoLongerNeeded

class ClosedReasonTest extends FunSuite {
  test("codec round trip") {
    val reasons = List[ClosedReason](ClosedReason.AlreadyApplied, ClosedReason.DependencyRemoved)
    reasons.foreach { reason =>
      assertEquals(reason.asJson.as[ClosedReason], Right(reason))
    }
    assertEquals((ClosedReason.AlreadyApplied: ClosedReason).asJson.noSpaces, "\"already-applied\"")
    assertEquals(
      (ClosedReason.DependencyRemoved: ClosedReason).asJson.noSpaces,
      "\"dependency-removed\""
    )
  }

  test("decoding an unknown reason fails") {
    assert(io.circe.Json.fromString("closed-by-martians").as[ClosedReason].isLeft)
  }

  test("of maps every NoLongerNeeded") {
    assertEquals(ClosedReason.of(NoLongerNeeded.DependencyRemoved), ClosedReason.DependencyRemoved)
  }
}
