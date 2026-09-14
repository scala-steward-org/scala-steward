package org.scalasteward.core.repoconfig

import io.circe.parser
import io.circe.syntax.*
import munit.FunSuite
import org.scalasteward.core.util.Timestamp
import scala.concurrent.duration.*

class PullRequestFrequencyTest extends FunSuite {
  val epoch: Timestamp = Timestamp(0L)
  val cats = "org.typelevel:cats-core"
  val munit = "org.scalameta:munit"

  test("onSchedule") {
    val Right(thursday) = PullRequestFrequency.fromString("0 * ? * THU"): @unchecked
    val Right(notThursday) = PullRequestFrequency.fromString("0 * ? * MON-WED,FRI-SUN"): @unchecked
    assert(thursday.onSchedule(epoch))
    assert(!notThursday.onSchedule(epoch))
  }

  test("waitingTime: @asap") {
    val Right(freq) = PullRequestFrequency.fromString("@asap"): @unchecked
    assertEquals(freq.waitingTime(epoch, Timestamp(18.hours.toMillis), cats, None), None)
  }

  test("waitingTime: @daily") {
    val Right(freq) = PullRequestFrequency.fromString("@daily"): @unchecked
    assertEquals(
      freq.waitingTime(epoch, Timestamp(18.hours.toMillis), cats, None),
      Some(6.hours + 12631317.millis)
    )
  }

  test("waitingTime: timespan") {
    val Right(freq) = PullRequestFrequency.fromString("14 days"): @unchecked
    assertEquals(
      freq.waitingTime(epoch, Timestamp(18.hours.toMillis), cats, None),
      Some(6.hours + 13.days + 142231317.millis)
    )
  }

  test("waitingTime: timespan, maximum spread") {
    val Right(freq) = PullRequestFrequency.fromString("14 days"): @unchecked
    val now = Timestamp(18.hours.toMillis)
    assertEquals(freq.waitingTime(epoch, now, cats, Some(0.days)), Some(6.hours + 13.days))
    assertEquals(
      freq.waitingTime(epoch, now, cats, Some(1.day)),
      Some(6.hours + 13.days + 55831317.millis)
    )
  }

  test("waitingTime: timespan, two dependencies") {
    val Right(freq) = PullRequestFrequency.fromString("14 days"): @unchecked
    val now = Timestamp(18.hours.toMillis)
    val base = 6.hours + 13.days
    val Some(a) = freq.waitingTime(epoch, now, cats, None): @unchecked
    val Some(b) = freq.waitingTime(epoch, now, munit, None): @unchecked
    assertNotEquals(a, b)
    assert(a >= base && a < base + 14.days / 4, a.toString)
    assert(b >= base && b < base + 14.days / 4, b.toString)
  }

  test("waitingTime: cron expr") {
    val Right(freq) = PullRequestFrequency.fromString("0 1 ? * *"): @unchecked
    val now = Timestamp(20.minutes.toMillis)
    assertEquals(freq.waitingTime(epoch, now, cats, None), Some(40.minutes))
    assertEquals(freq.waitingTime(epoch, now, cats, Some(1.day)), Some(40.minutes))
  }

  test("CronExpr encode and then decode") {
    val Right(freq) = PullRequestFrequency.fromString("0 0 1,15 * ?"): @unchecked
    assertEquals(parser.decode[PullRequestFrequency](freq.asJson.spaces2), Right(freq))
  }
}
