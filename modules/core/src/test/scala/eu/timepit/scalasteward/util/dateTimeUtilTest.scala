package eu.timepit.scalasteward.util

import eu.timepit.scalasteward.util.dateTimeUtil._
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.duration._

class dateTimeUtilTest extends FunSuite with Matchers {
  test("showDuration") {
    showDuration(247023586491264L.nanoseconds) shouldBe "2d 20h 37m 3s 586ms 491µs 264ns"
  }
}
