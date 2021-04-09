package org.scalasteward.core.repoconfig

import io.circe.parser
import io.circe.syntax._
import munit.FunSuite
import org.scalasteward.core.util.Timestamp
import scala.concurrent.duration._

class PullRequestFrequencyTest extends FunSuite {
  val epoch: Timestamp = Timestamp(0L)

  test("onSchedule") {
    val Right(thursday) = PullRequestFrequency.fromString("0 * ? * THU")
    val Right(notThursday) = PullRequestFrequency.fromString("0 * ? * MON-WED,FRI-SUN")
    assert(thursday.onSchedule(epoch))
    assert(!notThursday.onSchedule(epoch))
  }

  test("waitingTime: @asap") {
    val Right(freq) = PullRequestFrequency.fromString("@asap")
    assertEquals(freq.waitingTime(epoch, Timestamp(18.hours.toMillis)), None)
  }

  test("waitingTime: @daily") {
    val Right(freq) = PullRequestFrequency.fromString("@daily")
    assertEquals(freq.waitingTime(epoch, Timestamp(18.hours.toMillis)), Some(6.hours))
  }

  test("waitingTime: timespan") {
    val Right(freq) = PullRequestFrequency.fromString("14 days")
    assertEquals(freq.waitingTime(epoch, Timestamp(18.hours.toMillis)), Some(6.hours + 13.days))
  }

  test("waitingTime: cron expr") {
    val Right(freq) = PullRequestFrequency.fromString("0 1 ? * *")
    assertEquals(freq.waitingTime(epoch, Timestamp(20.minutes.toMillis)), Some(40.minutes))
  }

  test("CronExpr encode and then decode") {
    val Right(freq) = PullRequestFrequency.fromString("0 0 1,15 * ?")
    assertEquals(parser.decode[PullRequestFrequency](freq.asJson.spaces2), Right(freq))
  }
}
