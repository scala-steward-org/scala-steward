package eu.timepit.scalasteward.sbt

import eu.timepit.scalasteward.MockState
import eu.timepit.scalasteward.MockState.MockEnv
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import eu.timepit.scalasteward.util.MockLogger
import org.scalatest.{FunSuite, Matchers}

class SbtAlgTest extends FunSuite with Matchers {
  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val loggerAlg: MockLogger = new MockLogger
  implicit val processAlg: MockProcessAlg = new MockProcessAlg
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg
  val sbtAlg: SbtAlg[MockEnv] = SbtAlg.create

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
