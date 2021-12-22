package org.scalasteward.core.application

import cats.effect.ExitCode
import munit.CatsEffectSuite
import org.scalasteward.core.mock.MockContext.context.stewardAlg
import org.scalasteward.core.mock.MockState

class StewardAlgTest extends CatsEffectSuite {
  test("runF") {
    val exitCode = stewardAlg.runF.runA(MockState.empty)
    exitCode.map(assertEquals(_, ExitCode.Success))
  }
}
