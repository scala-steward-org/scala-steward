package org.scalasteward.core.util

import cats.syntax.all._
import org.scalasteward.core.util.dateTime._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scala.concurrent.duration._

class dateTimeTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
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
