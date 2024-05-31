package org.scalasteward.core.application

import cats.effect.ExitCode
import munit.CatsEffectSuite
import org.scalasteward.core.mock.MockContext.context.stewardAlg
import org.scalasteward.core.mock.{MockConfig, MockState}

class StewardAlgTest extends CatsEffectSuite {
  test("runF") {
    val exitCode = stewardAlg.runF.runA(MockState.empty.addUris(MockConfig.reposFile -> ""))
    assertIO(
      exitCode,
      ExitCode.Error
    ) // We have not passed any repos to Scala Steward therefore it's reasonable that it will return error code.
  }
}
