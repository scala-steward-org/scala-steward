package org.scalasteward.core.repoconfig

import org.scalasteward.core.util.Timestamp
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class PullRequestFrequencyTest extends AnyFunSuite with Matchers {
  val epoch: Timestamp = Timestamp(0L)

  test("onSchedule") {
    val thursday = PullRequestFrequency.fromString("0 0 * ? * 3")
    val notThursday = PullRequestFrequency.fromString("0 0 * ? * 0,1,2,4,5,6")
    thursday.onSchedule(epoch) shouldBe true
    notThursday.onSchedule(epoch) shouldBe false
  }

  test("waitingTime: @daily") {
    val freq = PullRequestFrequency.fromString("@daily")
    freq.waitingTime(epoch, Timestamp(18.hours.toMillis)) shouldBe Some(6.hours)
  }

  test("waitingTime: cron expr") {
    val freq = PullRequestFrequency.fromString("0 0 * ? * *")
    freq.waitingTime(epoch, Timestamp(20.minutes.toMillis)) shouldBe Some(40.minutes)
  }
}
