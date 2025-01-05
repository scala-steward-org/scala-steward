package org.scalasteward.core.application

import cats.effect.ExitCode
import munit.CatsEffectSuite
import org.scalasteward.core.mock.MockContext.context.stewardAlg
import org.scalasteward.core.mock.{GitHubAuth, MockConfig, MockEffOps, MockState}

class StewardAlgTest extends CatsEffectSuite {
  test("runF") {
    val exitCode = stewardAlg.runF.runA(
      MockState.empty
        .copy(clientResponses = GitHubAuth.api(List.empty))
        .addUris(MockConfig.reposFile -> "")
    )
    assertIO(exitCode, ExitCode.Success)
  }
}
