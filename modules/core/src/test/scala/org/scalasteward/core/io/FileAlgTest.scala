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

  test("editSourceFiles") {
    val file1 = File.root / "tmp" / "steward" / "test1.sbt"
    val file2 = File.root / "tmp" / "steward" / "test2.scala"
    val (state, edited) = (for {
      _ <- mockFileAlg.writeFile(file1, "123")
      _ <- mockFileAlg.writeFile(file2, "456")
      edit = (s: String) => if (s.contains("2")) Some(s.replace("2", "8")) else None
      edited <- mockFileAlg.editSourceFiles(file1.parent, edit)
    } yield edited).run(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", file1.pathAsString),
        List("write", file2.pathAsString),
        List("read", file1.pathAsString),
        List("write", file1.pathAsString),
        List("read", file2.pathAsString)
      ),
      files = Map(file1 -> "183", file2 -> "456")
    )
    edited shouldBe true
  }
}
