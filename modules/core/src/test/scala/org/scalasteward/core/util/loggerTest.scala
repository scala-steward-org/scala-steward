package org.scalasteward.core.util

import munit.CatsEffectSuite
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState.TraceEntry.Log
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.util.logger.LoggerOps

class loggerTest extends CatsEffectSuite {
  test("attemptError.bracket_") {
    final case class Err(msg: String) extends Throwable(msg)
    val err = Err("hmm?")
    logger.attemptError.bracket_("run")(MockEff.raiseError(err)).runS(MockState.empty).map {
      state =>
        assertEquals(state.trace, Vector(Log("run"), Log((Some(err), "run failed"))))
    }
  }

  test("infoTimed") {
    logger.infoTimed(_ => "timed")(logger.info("inner")).runS(MockState.empty).map { state =>
      assertEquals(state.trace, Vector(Log("inner"), Log("timed")))
    }
  }

  test("infoTotalTime") {
    logger.infoTotalTime("run")(MockEff.unit).runS(MockState.empty).map { state =>
      assertEquals(state.trace.size, 1)
    }
  }
}
