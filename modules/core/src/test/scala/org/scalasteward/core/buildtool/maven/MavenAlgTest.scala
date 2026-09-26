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

package org.scalasteward.core.buildtool.maven

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEffOps, MockState}

class MavenAlgTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("namespace", "repo-name")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()

    val state = mavenAlg.getDependencies(buildRoot).runS(MockState.empty).unsafeRunSync()
    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd.execSandboxed(
          repoDir,
          "mvn",
          args.batchMode,
          command.listDependencies,
          args.excludeTransitive
        ),
        Cmd.execSandboxed(
          repoDir,
          "mvn",
          args.batchMode,
          command.listRepositories
        )
      )
    )

    assertEquals(state, expected)
  }
}
