package org.scalasteward.core.sbt

import better.files.File
import org.scalasteward.core.application.Config
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalatest.{FunSuite, Matchers}

class SbtAlgTest extends FunSuite with Matchers {

  test("addGlobalPlugins") {
    sbtAlg.addGlobalPlugins.runS(MockState.empty).unsafeRunSync() shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", "/tmp/steward/.sbt/0.13/plugins/sbt-updates.sbt"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/sbt-updates.sbt"),
        List("write", "/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala")
      ),
      logs = Vector((None, "Add global sbt plugins")),
      files = Map(
        File("/tmp/steward/.sbt/0.13/plugins/sbt-updates.sbt") -> sbtUpdatesPlugin.content,
        File("/tmp/steward/.sbt/1.0/plugins/sbt-updates.sbt") -> sbtUpdatesPlugin.content,
        File("/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala") -> stewardPlugin.content,
        File("/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala") -> stewardPlugin.content
      )
    )
  }

  test("getUpdatesForRepo") {
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val state = sbtAlg.getUpdatesForRepo(repo).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";set every credentials := Nil;dependencyUpdates;reload plugins;dependencyUpdates"
        )
      ),
      extraEnv = Vector(
        List(("TEST_VAR", "GREAT"), ("ANOTHER_TEST_VAR", "ALSO_GREAT"))
      )
    )
  }

  test("getUpdatesForRepo ignoring .jvmopts and .sbtopts files") {
    implicit val config: Config = MockContext.config.copy(ignoreOptsFiles = true)
    val sbtAlgKeepingCredentials = SbtAlg.create
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val state =
      sbtAlgKeepingCredentials.getUpdatesForRepo(repo).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("rm", (repoDir / ".jvmopts").toString),
        List("rm", (repoDir / ".sbtopts").toString),
        List("create", (repoDir / ".jvmopts").toString),
        List(
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";set every credentials := Nil;dependencyUpdates;reload plugins;dependencyUpdates"
        ),
        List("rm", (repoDir / ".jvmopts").toString),
        List("restore", (repoDir / ".sbtopts").toString),
        List("restore", (repoDir / ".jvmopts").toString)
      ),
      extraEnv = Vector(
        List(("TEST_VAR", "GREAT"), ("ANOTHER_TEST_VAR", "ALSO_GREAT"))
      )
    )
  }

  test("getUpdatesForRepo keeping credentials") {
    implicit val config: Config = MockContext.config.copy(keepCredentials = true)
    val sbtAlgKeepingCredentials = SbtAlg.create
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val state =
      sbtAlgKeepingCredentials.getUpdatesForRepo(repo).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";dependencyUpdates;reload plugins;dependencyUpdates"
        )
      ),
      extraEnv = Vector(
        List(("TEST_VAR", "GREAT"), ("ANOTHER_TEST_VAR", "ALSO_GREAT"))
      )
    )
  }
}
