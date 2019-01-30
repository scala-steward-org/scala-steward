package org.scalasteward.core.nurture

import better.files.File
import org.scalasteward.core.MockState
import org.scalasteward.core.MockState.MockEff
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.io.{MockFileAlg, MockWorkspaceAlg}
import org.scalasteward.core.model.Update
import org.scalasteward.core.util.{MockLogger, Nel}
import org.scalatest.{FunSuite, Matchers}

class EditAlgTest extends FunSuite with Matchers {
  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val logger: MockLogger = new MockLogger
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg

  val editAlg: EditAlg[MockEff] = EditAlg.create

  test("applyUpdate") {
    val repo = Repo("fthomas", "scala-steward")
    val update = Update.Single("org.typelevel", "cats-core", "1.2.0", Nel.of("1.3.0"))
    val file = File("/tmp/ws/fthomas/scala-steward/build.sbt")

    val state = editAlg
      .applyUpdate(repo, update)
      .runS(MockState.empty.add(file, """val catsVersion = "1.2.0""""))
      .unsafeRunSync()

    state shouldBe MockState(
      commands = Vector(
        List("read", file.pathAsString),
        List("read", file.pathAsString),
        List("write", file.pathAsString)
      ),
      logs = Vector(
        (None, "Trying update strategy 'replaceAllInStrict'"),
        (None, "Trying update strategy 'replaceAllIn'")
      ),
      files = Map(file -> """val catsVersion = "1.3.0"""")
    )
  }
}
