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

package org.scalasteward.core.scalafmt

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.{Repo, Version}
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEffOps, MockState}

class ScalafmtAlgTest extends FunSuite {
  test("getScalafmtVersion on unquoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val scalafmtConf = repoDir / scalafmtConfName
    val initialState = MockState.empty
      .addFiles(scalafmtConf -> """maxColumn = 100
                                  |version=2.0.0-RC8
                                  |align.openParenCallSite = false
                                  |""".stripMargin)
      .unsafeRunSync()
    val (state, maybeVersion) =
      scalafmtAlg.getScalafmtVersion(buildRoot).runSA(initialState).unsafeRunSync()
    val expectedState = initialState.copy(
      trace = Vector(Cmd("read", scalafmtConf.toString))
    )

    assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))
    assertEquals(state, expectedState)
  }

  test("getScalafmtVersion on quoted version") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val scalafmtConf = repoDir / scalafmtConfName
    val initialState = MockState.empty
      .addFiles(scalafmtConf -> """maxColumn = 100
                                  |version="2.0.0-RC8"
                                  |align.openParenCallSite = false
                                  |""".stripMargin)
      .unsafeRunSync()
    val (_, maybeVersion) =
      scalafmtAlg.getScalafmtVersion(buildRoot).runSA(initialState).unsafeRunSync()
    assertEquals(maybeVersion, Some(Version("2.0.0-RC8")))
  }

  test("version with comment") {
    val obtained = ScalafmtAlg.parseScalafmtConf("version = 2.0.0 // comment")
    assertEquals(obtained, Right(Some(Version("2.0.0"))))
  }
}
