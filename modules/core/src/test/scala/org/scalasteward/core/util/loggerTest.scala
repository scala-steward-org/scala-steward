package org.scalasteward.core.util

import cats.effect.Sync
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.util.logger.LoggerOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class loggerTest extends AnyFunSuite with Matchers {
  test("attemptLog_") {
    final case class Err(msg: String) extends Throwable(msg)
    val err = Err("hmm?")
    val state = mockLogger
      .attemptLog_("run")(Sync[MockEff].raiseError(err))
      .runS(MockState.empty)
      .unsafeRunSync()

    state.logs shouldBe Vector((None, "run"), (Some(err), "run failed"))
  }

  test("infoTimed") {
    val state = mockLogger
      .infoTimed(_ => "timed")(mockLogger.info("inner"))
      .runS(MockState.empty)
      .unsafeRunSync()

    state.logs shouldBe Vector((None, "inner"), (None, "timed"))
  }
}
