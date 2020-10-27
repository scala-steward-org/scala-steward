package org.scalasteward.core.util

import cats.syntax.all._
import org.scalasteward.core.util.dateTime._
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scala.concurrent.duration._

class dateTimeTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
  def approxEq(x: FiniteDuration, y: FiniteDuration): Assertion = {
    val eps = 1.microsecond
    (x - y).toNanos.abs should be <= eps.toNanos
  }

  def parseFiniteDurationRoundTrips(fd: FiniteDuration): Assertion =
    parseFiniteDuration(fd.toString).fold(t => throw t, approxEq(_, fd))

  test("parseFiniteDuration") {
    forAll((d: FiniteDuration) => parseFiniteDurationRoundTrips(d))
  }

  test("parseFiniteDuration: #1693") {
    parseFiniteDurationRoundTrips(-6340054257704093L.microseconds)
  }

  test("showDuration: example 1") {
    val d = 2.days + 20.hours + 37.minutes + 3.seconds + 586.millis + 491.micros + 264.nanos
    showDuration(d) shouldBe "2d 20h 37m 3s 586ms 491µs 264ns"
  }

  test("showDuration: example 2") {
    showDuration(60.minutes + 30.seconds + 1000.millis) shouldBe "1h 31s"
  }

  test("showDuration: example 3") {
    showDuration(23.hours + 59.minutes + 59.seconds + 1000.millis) shouldBe "1d"
  }

  test("splitDuration and combineAll is identity") {
    forAll((d: FiniteDuration) => splitDuration(d).combineAll.eqv(d))
  }
}
