package org.scalasteward.core.sbt

import better.files.File
import org.scalasteward.core.application.Config
import org.scalasteward.core.github.data.Repo
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
    val state = sbtAlg.getUpdatesForRepo(repo).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("rm", (config.workspace / "fthomas/refined/.jvmopts").toString),
        List("rm", (config.workspace / "fthomas/refined/.sbtopts").toString),
        List(
          "firejail",
          s"--whitelist=${(config.workspace / "fthomas/refined").toString}",
          "sbt",
          "-batch",
          "-no-colors",
          ";set every credentials := Nil;dependencyUpdates;reload plugins;dependencyUpdates"
        ),
        List("restore", (config.workspace / "fthomas/refined/.sbtopts").toString),
        List("restore", (config.workspace / "fthomas/refined/.jvmopts").toString)
      ),
      extraEnv = Vector(
        List(("TEST_VAR", "GREAT"), ("ANOTHER_TEST_VAR", "ALSO_GREAT"))
      )
    )
  }

  test("getUpdatesForRepo keeping credentials") {
    val repo = Repo("fthomas", "refined")
    implicit val config: Config = MockContext.config.copy(keepCredentials = true)
    val sbtAlgKeepingCredentials = SbtAlg.create
    val state =
      sbtAlgKeepingCredentials.getUpdatesForRepo(repo).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("rm", (config.workspace / "fthomas/refined/.jvmopts").toString),
        List("rm", (config.workspace / "fthomas/refined/.sbtopts").toString),
        List(
          "firejail",
          s"--whitelist=${(config.workspace / "fthomas/refined").toString}",
          "sbt",
          "-batch",
          "-no-colors",
          ";dependencyUpdates;reload plugins;dependencyUpdates"
        ),
        List("restore", (config.workspace / "fthomas/refined/.sbtopts").toString),
        List("restore", (config.workspace / "fthomas/refined/.jvmopts").toString)
      ),
      extraEnv = Vector(
        List(("TEST_VAR", "GREAT"), ("ANOTHER_TEST_VAR", "ALSO_GREAT"))
      )
    )
  }
}
