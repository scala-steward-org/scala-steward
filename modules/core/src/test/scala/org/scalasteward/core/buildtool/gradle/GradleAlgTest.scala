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

package org.scalasteward.core.buildtool.gradle

import munit.CatsEffectSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.{Repo, Scope}
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.{MockEffOps, MockState}

class GradleAlgTest extends CatsEffectSuite {
  test("getDependencies") {
    val repo = Repo("gradle-alg", "test-getDependencies")
    val buildRoot = BuildRoot(repo, ".")
    val buildRootDir = workspaceAlg.buildRootDir(buildRoot).unsafeRunSync()

    val initial = MockState.empty.addFiles(
      buildRootDir / "gradle" / libsVersionsTomlName ->
        """|[libraries]
           |tomlj = { group = "org.tomlj", name = "tomlj", version = "1.1.1" }
           |[plugins]
           |kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version = "2.1.20-Beta1" }
           |""".stripMargin
    )
    val obtained = initial.flatMap(gradleAlg.getDependencies(buildRoot).runA)
    val kotlinJvm =
      "org.jetbrains.kotlin.jvm".g % "org.jetbrains.kotlin.jvm.gradle.plugin".a % "2.1.20-Beta1"
    val expected = List(
      List("org.tomlj".g % "tomlj".a % "1.1.1").withMavenCentral,
      Scope(List(kotlinJvm), List(pluginsResolver))
    )
    assertIO(obtained, expected)
  }
}
