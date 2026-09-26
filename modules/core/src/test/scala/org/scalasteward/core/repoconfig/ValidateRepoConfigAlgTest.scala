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

package org.scalasteward.core.repoconfig

import cats.effect.ExitCode
import munit.CatsEffectSuite
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.validateRepoConfigContext.validateRepoConfigAlg
import org.scalasteward.core.mock.{MockEffOps, MockState}

class ValidateRepoConfigAlgTest extends CatsEffectSuite {
  test("accepts valid config") {
    val file = mockRoot / ".scala-steward.conf"
    val content =
      """|updates.pin = [
         |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
         |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." }
         |]""".stripMargin
    val state = MockState.empty.addFiles(file -> content)
    val obtained = state.flatMap(validateRepoConfigAlg.validateAndReport(file).runA)
    assertIO(obtained, ExitCode.Success)
  }

  test("rejects config with a parsing failure") {
    val file = mockRoot / ".scala-steward.conf"
    val content =
      """|updates.pin  =? [
         |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
         |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." },
         |]""".stripMargin
    val state = MockState.empty.addFiles(file -> content)
    val obtained = state.flatMap(validateRepoConfigAlg.validateAndReport(file).runA)
    assertIO(obtained, ExitCode.Error)
  }

  test("rejects config with a decoding failure") {
    val file = mockRoot / ".scala-steward.conf"
    val content = """updatePullRequests = 123""".stripMargin
    val state = MockState.empty.addFiles(file -> content)
    val obtained = state.flatMap(validateRepoConfigAlg.validateAndReport(file).runA)
    assertIO(obtained, ExitCode.Error)
  }

  test("rejects non-existent config file") {
    val file = mockRoot / ".scala-steward.conf"
    val obtained = validateRepoConfigAlg.validateAndReport(file).runA(MockState.empty)
    assertIO(obtained, ExitCode.Error)
  }
}
