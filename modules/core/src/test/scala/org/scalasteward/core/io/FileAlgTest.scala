package org.scalasteward.core.io

import better.files.File
import org.scalasteward.core.MockState
import org.scalatest.{FunSuite, Matchers}

class FileAlgTest extends FunSuite with Matchers {
  val mockFileAlg: MockFileAlg = new MockFileAlg

  test("editFile: nonexistent file") {
    val (state, edited) = (for {
      home <- mockFileAlg.home
      edited <- mockFileAlg.editFile(home / "does-not-exists.txt", Some.apply)
    } yield edited).run(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", "/tmp/steward/does-not-exists.txt")
      )
    )
    edited shouldBe false
  }

  test("editFile: existent file") {
    val file = File.root / "tmp" / "steward" / "test1.sbt"
    val (state, edited) = (for {
      _ <- mockFileAlg.writeFile(file, "123")
      edit = (s: String) => Some(s.replace("2", "4"))
      edited <- mockFileAlg.editFile(file, edit)
    } yield edited).run(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", file.pathAsString),
        List("read", file.pathAsString),
        List("write", file.pathAsString)
      ),
      files = Map(file -> "143")
    )
    edited shouldBe true
  }
}
