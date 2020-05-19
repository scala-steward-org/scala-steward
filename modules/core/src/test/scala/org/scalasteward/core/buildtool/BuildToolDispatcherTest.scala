package org.scalasteward.core.buildtool

import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.mock.MockContext.{buildToolDispatcher, config}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BuildToolDispatcherTest extends AnyFunSuite with Matchers {
  test("getDependencies") {
    val repo = Repo("typelevel", "cats")
    val repoDir = config.workspace / repo.show
    val initial = MockState.empty
    val state = buildToolDispatcher.getDependencies(repo).runS(initial).unsafeRunSync()

    state shouldBe initial.copy(commands =
      Vector(
        List("test", "-f", s"$repoDir/build.sbt"),
        List("test", "-f", s"$repoDir/pom.xml"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          s";$setOffline;$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        List("read", s"$repoDir/project/build.properties"),
        List("read", s"$repoDir/.scalafmt.conf")
      )
    )
  }
}
