package org.scalasteward.core.util

import org.scalasteward.core.util.dateTime._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class dateTimeTest extends AnyFunSuite with Matchers {
  test("showDuration") {
    showDuration(247023586491264L.nanoseconds) shouldBe "2d 20h 37m 3s 586ms 491µs 264ns"
  }
}
