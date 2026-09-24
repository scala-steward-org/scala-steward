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

package org.scalasteward.core.buildtool.giter8

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEffOps, MockState}

class Giter8AlgTest extends FunSuite {

  test("getRenderedGiter8BuildRoot: returns the rendered build root") {
    val repo = Repo("example", "kafka-streams.g8")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val initial = MockState.empty
      .addFiles(
        repoDir / "src" / "main" / "g8" / "build.sbt" -> "name := $name$",
        repoDir / "target" / "g8" / "build.sbt" -> "name := \"example\""
      )
      .unsafeRunSync()
    val (state, result) =
      giter8Alg.getRenderedGiter8BuildRoot(repo).runSA(initial).unsafeRunSync()
    val expectedBuildRoot = BuildRoot(repo, "target/g8")
    assertEquals(result, Some(expectedBuildRoot))
    assert(
      state.trace.contains(
        Cmd.execSandboxed(
          repoDir,
          "sbt",
          "--server",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          ";g8"
        )
      )
    )
  }

  test("getRenderedGiter8BuildRoot: repository name does not need to end with .g8") {
    val repo = Repo("example", "kafka-streams")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val initial = MockState.empty
      .addFiles(
        repoDir / "src" / "main" / "g8" / "build.sbt" -> "lazy val root = ...",
        repoDir / "target" / "g8" / "build.sbt" -> "lazy val root = ..."
      )
      .unsafeRunSync()
    val result = giter8Alg.getRenderedGiter8BuildRoot(repo).runA(initial).unsafeRunSync()
    val expectedBuildRoot = BuildRoot(repo, "target/g8")
    assertEquals(result, Some(expectedBuildRoot))
  }

  test("getRenderedGiter8BuildRoot: returns None when no giter8 template exists") {
    val repo = Repo("example", "regular-project")
    val initial = MockState.empty
    val result = giter8Alg.getRenderedGiter8BuildRoot(repo).runA(initial).unsafeRunSync()
    assertEquals(result, None)
  }

  test("getRenderedGiter8BuildRoot: a .g8 suffix alone is not a src-layout template") {
    val repo = Repo("example", "my-template.g8")
    val initial = MockState.empty
    val result = giter8Alg.getRenderedGiter8BuildRoot(repo).runA(initial).unsafeRunSync()
    assertEquals(result, None)
  }

  test("getRenderedGiter8BuildRoot: returns None when the rendered template has no sbt build") {
    val repo = Repo("example", "non-sbt-template.g8")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val initial = MockState.empty
      .addFiles(repoDir / "src" / "main" / "g8" / "README.md" -> "Hello $name$")
      .unsafeRunSync()
    val result = giter8Alg.getRenderedGiter8BuildRoot(repo).runA(initial).unsafeRunSync()
    assertEquals(result, None)
  }
}
