package org.scalasteward.core.util

import cats.effect.IO
import org.scalasteward.core.util.dateTime._
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.duration._

class dateTimeTest extends FunSuite with Matchers {
  test("timed") {
    timed(IO.pure(42)).map(_._2.length >= 0).unsafeRunSync()
  }

  test("showDuration") {
    showDuration(247023586491264L.nanoseconds) shouldBe "2d 20h 37m 3s 586ms 491µs 264ns"
  }
}
