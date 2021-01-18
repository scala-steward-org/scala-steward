package org.scalasteward.core.buildtool.maven

import better.files.File
import munit.FunSuite
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockContext.context.mavenAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class MavenAlgTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("namespace", "repo-name")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.show
    val files: Map[File, String] = Map.empty

    val state =
      mavenAlg.getDependencies(buildRoot).runS(MockState.empty.copy(files = files)).unsafeRunSync()

    val expected = MockState.empty.copy(
      files = files,
      logs = Vector.empty,
      commands = Vector(
        List(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "mvn",
          "--batch-mode",
          command.listDependencies
        ),
        List(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "mvn",
          "--batch-mode",
          command.listRepositories
        )
      )
    )

    assertEquals(state, expected)
  }
}
