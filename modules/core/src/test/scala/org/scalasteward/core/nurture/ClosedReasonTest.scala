/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
