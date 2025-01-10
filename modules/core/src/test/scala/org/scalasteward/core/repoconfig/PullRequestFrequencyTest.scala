package org.scalasteward.core.repoconfig

import io.circe.parser
import io.circe.syntax.*
import munit.FunSuite
import org.scalasteward.core.util.Timestamp
import scala.concurrent.duration.*

class PullRequestFrequencyTest extends FunSuite {
  val epoch: Timestamp = Timestamp(0L)

  test("onSchedule") {
    val Right(thursday) = PullRequestFrequency.fromString("0 * ? * THU"): @unchecked
    val Right(notThursday) = PullRequestFrequency.fromString("0 * ? * MON-WED,FRI-SUN"): @unchecked
    assert(thursday.onSchedule(epoch))
    assert(!notThursday.onSchedule(epoch))
  }

  test("waitingTime: @asap") {
    val Right(freq) = PullRequestFrequency.fromString("@asap"): @unchecked
    assertEquals(freq.waitingTime(epoch, Timestamp(18.hours.toMillis)), None)
  }

  test("waitingTime: @daily") {
    val Right(freq) = PullRequestFrequency.fromString("@daily"): @unchecked
    assertEquals(freq.waitingTime(epoch, Timestamp(18.hours.toMillis)), Some(6.hours))
  }

  test("waitingTime: timespan") {
    val Right(freq) = PullRequestFrequency.fromString("14 days"): @unchecked
    assertEquals(freq.waitingTime(epoch, Timestamp(18.hours.toMillis)), Some(6.hours + 13.days))
  }

  test("waitingTime: cron expr") {
    val Right(freq) = PullRequestFrequency.fromString("0 1 ? * *"): @unchecked
    assertEquals(freq.waitingTime(epoch, Timestamp(20.minutes.toMillis)), Some(40.minutes))
  }

  test("CronExpr encode and then decode") {
    val Right(freq) = PullRequestFrequency.fromString("0 0 1,15 * ?"): @unchecked
    assertEquals(parser.decode[PullRequestFrequency](freq.asJson.spaces2), Right(freq))
  }
}
