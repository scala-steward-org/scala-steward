package org.scalasteward.core.repoconfig

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommitsConfigTest extends AnyFunSuite with Matchers {
  test("CommitsConfig: semigroup") {
    import cats.syntax.semigroup._

    val emptyCfg = CommitsConfig()
    emptyCfg |+| emptyCfg shouldBe emptyCfg

    val cfg = CommitsConfig(Some("message"))
    cfg |+| emptyCfg shouldBe cfg
    emptyCfg |+| cfg shouldBe cfg

    val cfg2 = CommitsConfig(Some("message2"))
    cfg |+| cfg2 shouldBe cfg
    cfg2 |+| cfg shouldBe cfg2
  }
}
