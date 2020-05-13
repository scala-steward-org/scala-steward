package org.scalasteward.core.buildsystem

import org.scalasteward.core.buildsystem.sbt.command._
import org.scalasteward.core.mock.MockContext.buildSystemDispatcher
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BuildSystemDispatcherTest extends AnyFunSuite with Matchers {
  test("getDependencies") {
    val repo = Repo("typelevel", "cats")
    val initial = MockState.empty
    val state = buildSystemDispatcher.getDependencies(repo).runS(initial).unsafeRunSync()

    state shouldBe initial.copy(commands =
      Vector(
        List("test", "-f", "/tmp/ws/typelevel/cats/build.sbt"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          "/tmp/ws/typelevel/cats",
          "firejail",
          "--whitelist=/tmp/ws/typelevel/cats",
          "sbt",
          "-batch",
          "-no-colors",
          s";$setOffline;$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        List("read", "/tmp/ws/typelevel/cats/project/build.properties"),
        List("read", "/tmp/ws/typelevel/cats/.scalafmt.conf")
      )
    )
  }
}
