package org.scalasteward.core.sbt

import org.scalasteward.core.MockState
import org.scalasteward.core.MockState.MockEnv
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import org.scalasteward.core.util.MockLogger
import org.scalatest.{FunSuite, Matchers}

class SbtAlgTest extends FunSuite with Matchers {
  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val loggerAlg: MockLogger = new MockLogger
  implicit val processAlg: MockProcessAlg = new MockProcessAlg
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg
  val sbtAlg: SbtAlg[MockEnv] = SbtAlg.create

  test("addGlobalPlugins") {
    sbtAlg.addGlobalPlugins.runS(MockState.empty).value shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", "/tmp/steward/.sbt/0.13/plugins/sbt-scalafix.sbt"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/sbt-scalafix.sbt"),
        List("write", "/tmp/steward/.sbt/0.13/plugins/sbt-updates.sbt"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/sbt-updates.sbt"),
        List("write", "/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala")
      ),
      logs = Vector((None, "Add global sbt plugins"))
    )
  }

  test("getUpdatesForRepo") {
    val repo = Repo("fthomas", "refined")
    val state = sbtAlg.getUpdatesForRepo(repo).runS(MockState.empty).value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("rm", "/tmp/ws/fthomas/refined/.jvmopts"),
        List("rm", "/tmp/ws/fthomas/refined/.sbtopts"),
        List(
          "sandbox",
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
}
