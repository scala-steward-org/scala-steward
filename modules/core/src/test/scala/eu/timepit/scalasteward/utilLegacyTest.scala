package eu.timepit.scalasteward

import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.duration._

class utilLegacyTest extends FunSuite with Matchers {
  test("show Duration") {
    utilLegacy.show(247023586491264L.nanoseconds) shouldBe "2d 20h 37m 3s 586ms 491µs 264ns"
  }
}
