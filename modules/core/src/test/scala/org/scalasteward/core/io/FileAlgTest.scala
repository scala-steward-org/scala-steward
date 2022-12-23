package org.scalasteward.core.io

import better.files.File
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.Uri
import org.scalacheck.Arbitrary
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.context.fileAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd

class FileAlgTest extends FunSuite {
  test("createTemporarily") {
    val file = mockRoot / "test-scala-steward3.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")

    val p = for {
      before <- ioFileAlg.readFile(file)
      during <- ioFileAlg.createTemporarily(file, content).surround(ioFileAlg.readFile(file))
      after <- ioFileAlg.readFile(file)
    } yield (before, during, after)

    assertEquals(p.unsafeRunSync(), (None, Some(content), None))
  }

  test("writeFile *> readFile <* deleteForce") {
    val file = mockRoot / "test-scala-steward1.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")
    val read = ioFileAlg.writeFile(file, content) *>
      ioFileAlg.readFile(file).map(_.getOrElse("")) <*
      ioFileAlg.deleteForce(file)
    assertEquals(read.unsafeRunSync(), content)
  }

  test("removeTemporarily") {
    val file = mockRoot / "test-scala-steward2.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")

    val p = for {
      _ <- ioFileAlg.writeFile(file, content)
      before <- ioFileAlg.readFile(file)
      during <- ioFileAlg.removeTemporarily(file).surround(ioFileAlg.readFile(file))
      after <- ioFileAlg.readFile(file)
    } yield (before, during, after)

    assertEquals(p.unsafeRunSync(), (Some(content), None, Some(content)))
  }

  test("removeTemporarily: nonexistent file") {
    val file = mockRoot / "does-not-exists.txt"
    assertEquals(ioFileAlg.removeTemporarily(file).surround(IO.pure(42)).unsafeRunSync(), 42)
  }

  test("editFile: nonexistent file") {
    val (state, edited) = fileAlg
      .editFile(mockRoot / "does-not-exists.txt", Some.apply)
      .runSA(MockState.empty)
      .unsafeRunSync()

    val expected =
      MockState.empty.copy(trace = Vector(Cmd("read", s"$mockRoot/does-not-exists.txt")))
    assertEquals(state, expected)
    assert(!edited)
  }

  test("editFile: existent file") {
    val file = mockRoot / "steward" / "test1.sbt"
    val (state, edited) = (for {
      _ <- fileAlg.writeFile(file, "123")
      edit = (s: String) => Some(s.replace("2", "4"))
      edited <- fileAlg.editFile(file, edit)
    } yield edited).runSA(MockState.empty).unsafeRunSync()

    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("write", file.pathAsString),
        Cmd("read", file.pathAsString),
        Cmd("write", file.pathAsString)
      ),
      files = Map(file -> "143")
    )
    assertEquals(state, expected)
    assert(edited)
  }

  test("deleteForce removes dangling symlink in subdirectory") {
    val dir = mockRoot / "steward-symlink"
    val sub = dir / "sub"
    val regular = dir / "regular"
    val symlink = sub / "symlink"
    val p = for {
      _ <- IO(dir.delete(swallowIOExceptions = true))
      _ <- ioFileAlg.writeFile(regular, "I'm a regular file")
      _ <- IO(sub.createDirectory())
      _ <- IO(symlink.symbolicLinkTo(regular))
      _ <- ioFileAlg.deleteForce(regular)
      _ <- ioFileAlg.deleteForce(dir)
      symlinkExists <- IO(symlink.exists(File.LinkOptions.noFollow))
    } yield symlinkExists
    assertEquals(p.unsafeRunSync(), false)
  }

  test("readUri: local file without scheme") {
    val file = mockRoot / "steward" / "readUri.txt"
    val content = "42"
    val p = for {
      _ <- ioFileAlg.writeFile(file, content)
      read <- ioFileAlg.readUri(Uri.unsafeFromString(file.toString))
    } yield read
    assertEquals(p.unsafeRunSync(), content)
  }

  test("isRegularFile") {
    val dir = mockRoot / "steward" / "regular"
    val file = dir / "file.txt"
    val p = for {
      _ <- ioFileAlg.deleteForce(dir)
      r1 <- ioFileAlg.isRegularFile(file)
      _ <- ioFileAlg.writeFile(file, "content")
      r2 <- ioFileAlg.isRegularFile(file)
    } yield (r1, r2)
    assertEquals(p.unsafeRunSync(), (false, true))
  }
}

object FileAlgTest {
  implicit val ioFileAlg: FileAlg[IO] = FileAlg.create[IO]
}
