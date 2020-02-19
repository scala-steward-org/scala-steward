package org.scalasteward.core.repoconfig

import org.scalasteward.core.util.Timestamp
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class PullRequestFrequencyTest extends AnyFunSuite with Matchers {
  test("timeout: @daily") {
    val freq = PullRequestFrequency.fromString("@daily")
    freq.timeout(Timestamp(0L), Timestamp(18.hours.toMillis)) shouldBe Some(6.hours)
  }

  test("timeout: cron expr") {
    val freq = PullRequestFrequency.fromString("0 0 * ? * *")
    freq.timeout(Timestamp(0L), Timestamp(20.minutes.toMillis)) shouldBe Some(40.minutes)
  }
}
