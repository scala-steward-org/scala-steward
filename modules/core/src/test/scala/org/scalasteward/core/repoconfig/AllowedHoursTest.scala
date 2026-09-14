package org.scalasteward.core.repoconfig

import io.circe.parser
import io.circe.syntax.*
import munit.FunSuite
import org.scalasteward.core.util.Timestamp
import scala.concurrent.duration.*

class AllowedHoursTest extends FunSuite {
  def at(hour: Int): Timestamp = Timestamp(hour.hours.toMillis)

  test("matches: list of ranges") {
    val Right(allowed) = AllowedHours.fromString("18-23,0-7"): @unchecked
    assert(allowed.matches(at(18)))
    assert(allowed.matches(at(22)))
    assert(!allowed.matches(at(23)))
    assert(allowed.matches(at(0)))
    assert(allowed.matches(at(6)))
    assert(!allowed.matches(at(7)))
    assert(!allowed.matches(at(8)))
    assert(!allowed.matches(at(17)))
  }

  test("matches: range crossing midnight") {
    val Right(allowed) = AllowedHours.fromString("18-7"): @unchecked
    assert(allowed.matches(at(18)))
    assert(allowed.matches(at(23)))
    assert(allowed.matches(at(0)))
    assert(allowed.matches(at(6)))
    assert(!allowed.matches(at(7)))
    assert(!allowed.matches(at(8)))
    assert(!allowed.matches(at(17)))
  }

  test("matches: range ending at midnight") {
    val Right(allowed) = AllowedHours.fromString("18-24"): @unchecked
    assert(allowed.matches(at(18)))
    assert(allowed.matches(at(23)))
    assert(!allowed.matches(at(0)))
    val Right(wrapped) = AllowedHours.fromString("18-0"): @unchecked
    assert(wrapped.matches(at(23)))
    assert(!wrapped.matches(at(0)))
  }

  test("matches: single hour") {
    val Right(allowed) = AllowedHours.fromString("18"): @unchecked
    assert(allowed.matches(at(18)))
    assert(!allowed.matches(at(19)))
  }

  test("fromString: out-of-range hour") {
    assert(AllowedHours.fromString("18-31").isLeft)
    assert(AllowedHours.fromString("24").isLeft)
    assert(AllowedHours.fromString("24-3").isLeft)
  }

  test("fromString: malformed input") {
    assert(AllowedHours.fromString("").isLeft)
    assert(AllowedHours.fromString("18-").isLeft)
    assert(AllowedHours.fromString("18-23,").isLeft)
    assert(AllowedHours.fromString("18-23-7").isLeft)
    assert(AllowedHours.fromString("7-7").isLeft)
  }

  test("encode and then decode") {
    val Right(allowed) = AllowedHours.fromString("18-23,0-7"): @unchecked
    assertEquals(parser.decode[AllowedHours](allowed.asJson.spaces2), Right(allowed))
    val Right(overnight) = AllowedHours.fromString("18-7"): @unchecked
    assertEquals(parser.decode[AllowedHours](overnight.asJson.spaces2), Right(overnight))
    assertEquals(AllowedHours.fromString("7-8").map(_.render), Right("7"))
  }
}
