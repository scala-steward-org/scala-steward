package org.scalasteward.core.repoconfig

import cats.syntax.semigroup._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PullRequestsConfigTest extends AnyFunSuite with Matchers {
  test("PullRequestsConfig: semigroup") {
    val emptyCfg = PullRequestsConfig()
    emptyCfg |+| emptyCfg shouldBe emptyCfg

    val cfg = PullRequestsConfig(Some(PullRequestFrequency.Asap))
    cfg |+| emptyCfg shouldBe cfg
    emptyCfg |+| cfg shouldBe cfg

    val cfg2 = PullRequestsConfig(Some(PullRequestFrequency.Weekly))
    cfg |+| cfg2 shouldBe cfg
    cfg2 |+| cfg shouldBe cfg2
  }
}
