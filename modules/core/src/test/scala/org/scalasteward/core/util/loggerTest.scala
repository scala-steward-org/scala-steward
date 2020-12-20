package org.scalasteward.core.util

import cats.effect.Sync
import munit.FunSuite
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.util.logger.LoggerOps

class loggerTest extends FunSuite {
  test("attemptLog") {
    final case class Err(msg: String) extends Throwable(msg)
    val err = Err("hmm?")
    val state = mockLogger
      .attemptLogInfo("run")(Sync[MockEff].raiseError(err))
      .runS(MockState.empty)
      .unsafeRunSync()

    assertEquals(state.logs, Vector((None, "run"), (Some(err), "run failed")))
  }

  test("infoTimed") {
    val state = mockLogger
      .infoTimed(_ => "timed")(mockLogger.info("inner"))
      .runS(MockState.empty)
      .unsafeRunSync()

    assertEquals(state.logs, Vector((None, "inner"), (None, "timed")))
  }
}
