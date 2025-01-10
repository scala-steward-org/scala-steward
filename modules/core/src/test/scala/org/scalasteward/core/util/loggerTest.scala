package org.scalasteward.core.util

import munit.CatsEffectSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.Update
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.Log
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockState}
import org.scalasteward.core.util.logger.{showUpdates, LoggerOps}

class loggerTest extends CatsEffectSuite {
  test("attemptError.label_") {
    final case class Err(msg: String) extends Throwable(msg)
    val err = Err("hmm?")
    logger.attemptError.label_("run")(MockEff.raiseError(err)).runS(MockState.empty).map { state =>
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

  test("showUpdates") {
    val a = ("a".g % "a".a % "1.0.0" %> "1.0.1").single
    val b = ("a".g % "b".a % "1.0.0" %> "1.1.0").single
    val c = ("a".g % "c".a % "1.0.0" %> "2.0.0").single

    val list = List(
      Update.Grouped("all", None, List(a, b, c)),
      Update.Grouped("some", None, List(a, b)),
      a,
      b,
      c
    )

    val result = showUpdates(list)

    val expected = """Found 5 updates:
                     |  all (group) {
                     |    a:a : 1.0.0 -> 1.0.1
                     |    a:b : 1.0.0 -> 1.1.0
                     |    a:c : 1.0.0 -> 2.0.0
                     |  }
                     |  some (group) {
                     |    a:a : 1.0.0 -> 1.0.1
                     |    a:b : 1.0.0 -> 1.1.0
                     |  }
                     |  a:a : 1.0.0 -> 1.0.1
                     |  a:b : 1.0.0 -> 1.1.0
                     |  a:c : 1.0.0 -> 2.0.0""".stripMargin

    assertEquals(result, expected)
  }

}
