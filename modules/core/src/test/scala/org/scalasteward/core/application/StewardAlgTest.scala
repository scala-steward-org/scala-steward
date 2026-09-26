/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
