package org.scalasteward.core.application

import cats.effect.ExitCode
import munit.CatsEffectSuite
import org.scalasteward.core.mock.MockContext.context.stewardAlg
import org.scalasteward.core.mock.MockState

class StewardAlgTest extends CatsEffectSuite {

  test("runF") {
    val stateExit = stewardAlg.runF.runSA(MockState.empty)
    assertIO(stateExit.map(_._2), ExitCode.Success) *>
      assertIOBoolean(
        stateExit.map(_._1.trace.exists(te => te.toString.contains("The workspace is empty")))
      )
  }
}
