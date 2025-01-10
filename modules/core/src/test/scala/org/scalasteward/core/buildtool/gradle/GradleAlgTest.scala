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
