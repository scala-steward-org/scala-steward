package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import cats.effect.IO
import fs2.Stream
import org.scalasteward.core.MockState
import org.scalasteward.core.MockState.MockEnv

class MockFileAlg extends FileAlg[MockEnv] {
  override def deleteForce(file: File): MockEnv[Unit] =
    StateT.modify(s => s.exec(List("rm", "-rf", file.pathAsString)).copy(files = s.files - file))

  override def ensureExists(dir: File): MockEnv[File] =
    StateT(s => IO.pure((s.exec(List("mkdir", "-p", dir.pathAsString)), dir)))

  override def home: MockEnv[File] =
    StateT.pure(File.root / "tmp" / "steward")

  override def isSymlink(file: File): MockEnv[Boolean] =
    StateT.pure(false)

  override def removeTemporarily[A](file: File)(fa: MockEnv[A]): MockEnv[A] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("rm", file.pathAsString)))
      a <- fa
      _ <- StateT.modify[IO, MockState](_.exec(List("restore", file.pathAsString)))
    } yield a

  override def readFile(file: File): MockEnv[Option[String]] =
    StateT(s => IO.pure((s.exec(List("read", file.pathAsString)), s.files.get(file))))

  override def walk(dir: File): Stream[MockEnv, File] = {
    val dirAsString = dir.pathAsString
    val state = StateT { s: MockState =>
      val files = s.files.keys.filter(_.pathAsString.startsWith(dirAsString)).toList
      IO.pure((s, files))
    }
    Stream.eval(state).flatMap(Stream.emits[MockEnv, File])
  }

  override def writeFile(file: File, content: String): MockEnv[Unit] =
    StateT.modify { s =>
      s.exec(List("write", file.pathAsString)).copy(files = s.files + ((file, content)))
    }
}
