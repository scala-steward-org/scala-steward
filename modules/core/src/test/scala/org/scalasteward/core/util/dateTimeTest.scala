package org.scalasteward.core.util

import cats.syntax.all._
import munit.ScalaCheckSuite
import org.scalacheck.Prop._
import org.scalasteward.core.util.dateTime._
import scala.concurrent.duration._

class dateTimeTest extends ScalaCheckSuite {
  def approxEq(x: FiniteDuration, y: FiniteDuration): Unit = {
    val eps = 1.microsecond
    assert(clue((x - y).toNanos.abs) <= clue(eps.toNanos))
  }

  def parseFiniteDurationRoundTrips(fd: FiniteDuration): Unit =
    parseFiniteDuration(fd.toString).fold(t => throw t, approxEq(_, fd))

  property("parseFiniteDuration") {
    forAll((d: FiniteDuration) => parseFiniteDurationRoundTrips(d))
  }

  test("parseFiniteDuration: #1693") {
    parseFiniteDurationRoundTrips(-6340054257704093L.microseconds)
  }

  test("showDuration: example 1") {
    val d = 2.days + 20.hours + 37.minutes + 3.seconds + 586.millis + 491.micros + 264.nanos
    assertEquals(showDuration(d), "2d 20h 37m 3s 586ms 491µs 264ns")
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
