package org.scalasteward.core.nurture

import better.files.File
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext.editAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.model.Update
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class EditAlgTest extends FunSuite with Matchers {

  test("applyUpdate") {
    val repo = Repo("fthomas", "scala-steward")
    val update = Update.Single("org.typelevel", "cats-core", "1.2.0", Nel.of("1.3.0"))
    val file = File.temp / "ws/fthomas/scala-steward/build.sbt"

    val state = editAlg
      .applyUpdate(repo, update)
      .runS(MockState.empty.add(file, """val catsVersion = "1.2.0""""))
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
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
