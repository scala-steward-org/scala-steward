package org.scalasteward.core.application

import cats.effect.ExitCode
import munit.FunSuite
import org.scalasteward.core.mock.MockContext.context.stewardAlg
import org.scalasteward.core.mock.MockState

class StewardAlgTest extends FunSuite {
  test("runF") {
    val exitCode = stewardAlg.runF.runA(MockState.empty).unsafeRunSync()
    assertEquals(exitCode, ExitCode.Success)
  }
}
