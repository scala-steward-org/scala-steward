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

package org.scalasteward.core.util

import cats.syntax.all.*
import munit.ScalaCheckSuite
import org.scalacheck.Prop.*
import org.scalasteward.core.util.dateTime.*
import scala.concurrent.duration.*

class dateTimeTest extends ScalaCheckSuite {
  def approxEq(x: FiniteDuration, y: FiniteDuration): Unit = {
    val eps = 1.microsecond
    assert(clue((x - y).toNanos.abs) <= clue(eps.toNanos))
  }

  def parseFiniteDurationRoundTrips(fd: FiniteDuration): Unit =
    parseFiniteDuration(renderFiniteDuration(fd)).fold(t => throw t, approxEq(_, fd))

  property("parseFiniteDuration") {
    forAll((d: FiniteDuration) => parseFiniteDurationRoundTrips(d))
  }

  test("parseFiniteDuration: #1693") {
    parseFiniteDurationRoundTrips(-6340054257704093L.microseconds)
  }

  test("parseFiniteDuration: invalid input") {
    assert(clue(parseFiniteDuration("Inf").isLeft))
  }

  test("parseFiniteDuration: examples") {
    assertEquals(parseFiniteDuration("30days"), Right(30.days))
    assertEquals(parseFiniteDuration("30 days"), Right(30.days))
  }

  test("showDuration: example 1") {
    val d = 2.days + 20.hours + 37.minutes + 3.seconds + 586.millis + 491.micros + 264.nanos
    assertEquals(showDuration(d), "2d 20h 37m")
  }

  test("showDuration: example 2") {
    assertEquals(showDuration(60.minutes + 30.seconds + 1000.millis), "1h 31s")
  }

  test("showDuration: example 3") {
    assertEquals(showDuration(23.hours + 59.minutes + 59.seconds + 1000.millis), "1d")
  }

  property("splitDuration and combineAll is identity") {
    forAll((d: FiniteDuration) => splitDuration(d).combineAll.eqv(d))
  }
}
