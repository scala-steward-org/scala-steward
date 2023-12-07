package org.scalasteward.core.io

import better.files.File
import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.Uri
import org.scalacheck.Arbitrary
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.context.fileAlg
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd

class FileAlgTest extends CatsEffectSuite {
  test("createTemporarily") {
    val file = mockRoot / "test-scala-steward3.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")

    val obtained = for {
      before <- ioFileAlg.readFile(file)
      during <- ioFileAlg.createTemporarily(file, content).surround(ioFileAlg.readFile(file))
      after <- ioFileAlg.readFile(file)
    } yield (before, during, after)

    assertIO(obtained, (None, Some(content), None))
  }

  test("writeFile *> readFile <* deleteForce") {
    val file = mockRoot / "test-scala-steward1.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")
    val obtained = ioFileAlg.writeFile(file, content) *>
      ioFileAlg.readFile(file).map(_.getOrElse("")) <*
      ioFileAlg.deleteForce(file)
    assertIO(obtained, content)
  }

  test("removeTemporarily") {
    val file = mockRoot / "test-scala-steward2.tmp"
    val content = Arbitrary.arbitrary[String].sample.getOrElse("")

    val obtained = for {
      _ <- ioFileAlg.writeFile(file, content)
      before <- ioFileAlg.readFile(file)
      during <- ioFileAlg.removeTemporarily(file).surround(ioFileAlg.readFile(file))
      after <- ioFileAlg.readFile(file)
    } yield (before, during, after)

    assertIO(obtained, (Some(content), None, Some(content)))
  }

  test("removeTemporarily: nonexistent file") {
    val file = mockRoot / "does-not-exist.txt"
    val obtained = ioFileAlg.removeTemporarily(file).surround(IO.pure(42))
    assertIO(obtained, 42)
  }

  test("editFile: nonexistent file") {
    val obtained = fileAlg
      .editFile(mockRoot / "does-not-exist.txt", MockEff.pure)
      .runS(MockState.empty)

    val expected =
      MockState.empty.copy(trace = Vector(Cmd("read", s"$mockRoot/does-not-exist.txt")))
    assertIO(obtained, expected)
  }

  test("editFile: existent file") {
    val file = mockRoot / "steward" / "test1.sbt"
    val obtained = (for {
      _ <- fileAlg.writeFile(file, "123")
      _ <- fileAlg.editFile(file, content => MockEff.pure(content.replace("2", "4")))
    } yield ()).runS(MockState.empty)

    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("write", file.pathAsString),
        Cmd("read", file.pathAsString),
        Cmd("write", file.pathAsString)
      ),
      files = Map(file -> "143")
    )
    assertIO(obtained, expected)
  }

  test("deleteForce removes dangling symlink in subdirectory") {
    val dir = mockRoot / "steward-symlink"
    val sub = dir / "sub"
    val regular = dir / "regular"
    val symlink = sub / "symlink"
    val obtained = for {
      _ <- IO(dir.delete(swallowIOExceptions = true))
      _ <- ioFileAlg.writeFile(regular, "I'm a regular file")
      _ <- IO(sub.createDirectory())
      _ <- IO(symlink.symbolicLinkTo(regular))
      _ <- ioFileAlg.deleteForce(regular)
      _ <- ioFileAlg.deleteForce(dir)
      symlinkExists <- IO(symlink.exists(File.LinkOptions.noFollow))
    } yield symlinkExists
    assertIO(obtained, false)
  }

  test("readUri: local file without scheme") {
    val file = mockRoot / "steward" / "readUri.txt"
    val content = "42"
    val obtained = for {
      _ <- ioFileAlg.writeFile(file, content)
      read <- ioFileAlg.readUri(Uri.unsafeFromString(file.toString))
    } yield read
    assertIO(obtained, content)
  }

  test("isRegularFile") {
    val dir = mockRoot / "steward" / "regular"
    val file = dir / "file.txt"
    val obtained = for {
      _ <- ioFileAlg.deleteForce(dir)
      r1 <- ioFileAlg.isRegularFile(file)
      _ <- ioFileAlg.writeFile(file, "content")
      r2 <- ioFileAlg.isRegularFile(file)
    } yield (r1, r2)
    assertIO(obtained, (false, true))
  }

  test("isNonEmptyDirectory: empty") {
    val dir = mockRoot / "workspace2"
    val obtained = ioFileAlg.isNonEmptyDirectory(dir)
    assertIO(obtained, false)
  }

  test("isNonEmptyDirectory: non empty") {
    val dir = mockRoot / "workspace1"
    val file = dir / "file.txt"
    val obtained = for {
      _ <- ioFileAlg.writeFile(file, "42")
      read <- ioFileAlg.isNonEmptyDirectory(dir)
    } yield read
    assertIO(obtained, true)
  }
}

object FileAlgTest {
  implicit val ioFileAlg: FileAlg[IO] = FileAlg.create[IO]
}
