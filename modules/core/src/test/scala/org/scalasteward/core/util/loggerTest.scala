package org.scalasteward.core.util

import cats.{Applicative, ApplicativeThrow}
import munit.FunSuite
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.util.logger.LoggerOps

class loggerTest extends FunSuite {
  test("attemptLog") {
    final case class Err(msg: String) extends Throwable(msg)
    val err = Err("hmm?")
    val state = logger
      .attemptLogLabel("run")(ApplicativeThrow[MockEff].raiseError(err))
      .runS(MockState.empty)
      .unsafeRunSync()
    assertEquals(state.logs, Vector((None, "run"), (Some(err), "run failed")))
  }

  test("infoTimed") {
    val state = logger
      .infoTimed(_ => "timed")(logger.info("inner"))
      .runS(MockState.empty)
      .unsafeRunSync()
    assertEquals(state.logs, Vector((None, "inner"), (None, "timed")))
  }

  test("infoTotalTime") {
    val state = logger
      .infoTotalTime("run")(Applicative[MockEff].unit)
      .runS(MockState.empty)
      .unsafeRunSync()
    assertEquals(state.logs.size, 1)
  }
}
