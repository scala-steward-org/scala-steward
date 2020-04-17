package org.scalasteward.core.io

import better.files.File
import cats.effect.IO
import org.scalacheck.Arbitrary
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockContext.fileAlg
import org.scalasteward.core.mock.MockState
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FileAlgTest extends AnyFunSuite with Matchers {
  test("createTemporarily") {
    val file = File.temp / "test-scala-steward3.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")

    val p = for {
      before <- ioFileAlg.readFile(file)
      during <- ioFileAlg.createTemporarily(file, content)(ioFileAlg.readFile(file))
      after <- ioFileAlg.readFile(file)
    } yield (before, during, after)

    p.unsafeRunSync() shouldBe ((None, Some(content), None))
  }

  test("writeFile *> readFile <* deleteForce") {
    val file = File.temp / "test-scala-steward1.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")
    val read = ioFileAlg.writeFile(file, content) *>
      ioFileAlg.readFile(file).map(_.getOrElse("")) <*
      ioFileAlg.deleteForce(file)
    read.unsafeRunSync() shouldBe content
  }

  test("removeTemporarily") {
    val file = File.temp / "test-scala-steward2.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")

    val p = for {
      _ <- ioFileAlg.writeFile(file, content)
      before <- ioFileAlg.readFile(file)
      during <- ioFileAlg.removeTemporarily(file)(ioFileAlg.readFile(file))
      after <- ioFileAlg.readFile(file)
    } yield (before, during, after)

    p.unsafeRunSync() shouldBe ((Some(content), None, Some(content)))
  }

  test("removeTemporarily: nonexistent file") {
    val file = File.temp / "does-not-exists.txt"
    ioFileAlg.removeTemporarily(file)(IO.pure(42)).unsafeRunSync() shouldBe 42
  }

  test("editFile: nonexistent file") {
    val (state, edited) = (for {
      home <- fileAlg.home
      edited <- fileAlg.editFile(home / "does-not-exists.txt", Some.apply)
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
      _ <- fileAlg.writeFile(file, "123")
      edit = (s: String) => Some(s.replace("2", "4"))
      edited <- fileAlg.editFile(file, edit)
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

object FileAlgTest {
  implicit val ioFileAlg: FileAlg[IO] = FileAlg.create[IO]
}
