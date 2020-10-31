package org.scalasteward.core.buildtool.maven

import better.files.File
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MavenAlgTest extends AnyFunSuite with Matchers {
  test("getDependencies") {
    val repo = Repo("namespace", "repo-name")
    val repoDir = config.workspace / repo.show
    val files: Map[File, String] = Map.empty

    val state =
      mavenAlg.getDependencies(repo).runS(MockState.empty.copy(files = files)).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      files = files,
      logs = Vector.empty,
      commands = Vector(
        List(
          repoDir.toString,
          "firejail",
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
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "mvn",
          "--batch-mode",
          command.listRepositories
        )
      )
    )
  }
}
