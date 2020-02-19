package org.scalasteward.core.repoconfig

import org.scalasteward.core.util.Timestamp
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class PullRequestFrequencyTest extends AnyFunSuite with Matchers {
  val epoch: Timestamp = Timestamp(0L)

  test("onSchedule") {
    val Right(thursday) = PullRequestFrequency.fromString("0 0 * ? * THU")
    val Right(notThursday) = PullRequestFrequency.fromString("0 0 * ? * MON-WED,FRI-SUN")
    thursday.onSchedule(epoch) shouldBe true
    notThursday.onSchedule(epoch) shouldBe false
  }

  test("waitingTime: @daily") {
    val Right(freq) = PullRequestFrequency.fromString("@daily")
    freq.waitingTime(epoch, Timestamp(18.hours.toMillis)) shouldBe Some(6.hours)
  }

  test("waitingTime: cron expr") {
    val Right(freq) = PullRequestFrequency.fromString("0 0 * ? * *")
    freq.waitingTime(epoch, Timestamp(20.minutes.toMillis)) shouldBe Some(40.minutes)
  }
}
