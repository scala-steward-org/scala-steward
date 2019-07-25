package org.scalasteward.core.util

import cats.effect.Sync
import org.scalasteward.core.mock.MockContext.{logAlg, logger => logger1}
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalatest.{FunSuite, Matchers}

class LogAlgTest extends FunSuite with Matchers {
  test("attemptLog_") {
    final case class Err(msg: String) extends Throwable(msg)
    val err = Err("hmm?")
    val state =
      logAlg.attemptLog("run")(Sync[MockEff].raiseError(err)).runS(MockState.empty).unsafeRunSync()

    state.logs shouldBe Vector((None, "run"), (Some(err), "run failed"))
  }

  test("infoTimed") {
    val state = logAlg
      .infoTimed(_ => "timed")(logger1.info("inner"))
      .runS(MockState.empty)
      .unsafeRunSync()

    state.logs shouldBe Vector((None, "inner"), (None, "timed"))
  }
}
