package org.scalasteward.core.util

import org.scalasteward.core.mock.MockContext.{logAlg, logger => logger1}
import org.scalasteward.core.mock.MockState
import org.scalatest.{FunSuite, Matchers}

class LogAlgTest extends FunSuite with Matchers {
  test("infoTimed") {
    val state = logAlg
      .infoTimed(_ => "timed")(logger1.info("inner"))
      .runS(MockState.empty)
      .unsafeRunSync()

    state.logs shouldBe Vector((None, "inner"), (None, "timed"))
  }
}
