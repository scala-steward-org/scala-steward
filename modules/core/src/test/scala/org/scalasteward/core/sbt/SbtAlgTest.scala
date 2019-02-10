package org.scalasteward.core.sbt

import better.files.File
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.mock.MockContext.sbtAlg
import org.scalasteward.core.mock.MockState
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
        List("rm", "/tmp/ws/fthomas/refined/.jvmopts"),
        List("rm", "/tmp/ws/fthomas/refined/.sbtopts"),
        List(
          "firejail",
          "--whitelist=/tmp/ws/fthomas/refined",
          "sbt",
          "-batch",
          "-no-colors",
          ";set every credentials := Nil;dependencyUpdates;reload plugins;dependencyUpdates"
        ),
        List("restore", "/tmp/ws/fthomas/refined/.sbtopts"),
        List("restore", "/tmp/ws/fthomas/refined/.jvmopts")
      )
    )
  }

  test("getUpdatesForRepo keeping credentials") {
    val repo = Repo("fthomas", "refined")
    val configWithKeepCredentialsEnabled = ConfigTest.dummyConfig.copy(keepCredentials = true)
    val sbtAlgWithKeepCredentials = SbtAlg.create(
      configWithKeepCredentialsEnabled,
      fileAlg,
      loggerAlg,
      processAlg,
      workspaceAlg,
      implicitly[Monad[MockEnv]]
    )
    val state = sbtAlgWithKeepCredentials.getUpdatesForRepo(repo).runS(MockState.empty).value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("rm", "/tmp/ws/fthomas/refined/.jvmopts"),
        List("rm", "/tmp/ws/fthomas/refined/.sbtopts"),
        List(
          "firejail",
          "--whitelist=/tmp/ws/fthomas/refined",
          "sbt",
          "-batch",
          "-no-colors",
          ";dependencyUpdates;reload plugins;dependencyUpdates"
        ),
        List("restore", "/tmp/ws/fthomas/refined/.sbtopts"),
        List("restore", "/tmp/ws/fthomas/refined/.jvmopts")
      )
    )
  }
}
