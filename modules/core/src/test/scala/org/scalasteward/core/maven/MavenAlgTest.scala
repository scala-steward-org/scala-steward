package org.scalasteward.core.maven

import better.files.File
import org.scalasteward.core.mock.MockMavenContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MavenAlgTest extends AnyFunSuite with Matchers {

  val var1 = "TEST_VAR=GREAT"
  val var2 = "ANOTHER_TEST_VAR=ALSO_GREAT"

  test("getUpdatesForRepo") {
    val repo = Repo("namespace", "repo-name")
    val repoDir = config.workspace / repo.show
    val files: Map[File, String] = Map.empty

    mavenAlg.getUpdatesForRepo(repo).runS(
      MockState.empty.copy(files = files)
    ).unsafeRunSync() shouldBe MockState(
      Vector(
        List(
          var1, var2, s"$repoDir", "firejail", s"--whitelist=$repoDir",
          "mvn", "versions:display-dependency-updates", "-DallowMajorUpdates=false", "-DallowMinorUpdates=false", "-DallowIncrementalUpdates=true", "-DallowAnyUpdates=false"
        ),
        List(
          var1, var2, s"$repoDir", "firejail", s"--whitelist=$repoDir",
          "mvn", "versions:display-dependency-updates"),
        List(
          var1, var2, s"$repoDir", "firejail", s"--whitelist=$repoDir",
          "mvn", "versions:display-plugin-updates")
      ),
      logs = Vector(),
      files = Map()
    )

  }

  test("getDependencies") {
    val repo = Repo("namespace", "repo-name")
    val repoDir = config.workspace / repo.show
    val files: Map[File, String] = Map.empty

    val state = mavenAlg.getDependencies(repo).runS(
      MockState.empty.copy(files = files)).unsafeRunSync()

    state shouldBe MockState(
      files = files,
      logs = Vector.empty,
      commands = Vector(
        List(
          var1, var2, repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "mvn clean", "dependency:list"
        ),
      )
    )
  }

}
