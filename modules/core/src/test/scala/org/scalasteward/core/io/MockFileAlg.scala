package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import cats.effect.IO
import fs2.Stream
import org.scalasteward.core.mock.{MockEff, MockState}

class MockFileAlg extends FileAlg[MockEff] {
  override def deleteForce(file: File): MockEff[Unit] =
    StateT.modify(_.exec(List("rm", "-rf", file.pathAsString)).rm(file))

  override def ensureExists(dir: File): MockEff[File] =
    StateT(s => IO.pure((s.exec(List("mkdir", "-p", dir.pathAsString)), dir)))

  override def home: MockEff[File] =
    StateT.pure(File.root / "tmp" / "steward")

  override def isSymlink(file: File): MockEff[Boolean] =
    StateT.pure(false)

  override def removeTemporarily[A](file: File)(fa: MockEff[A]): MockEff[A] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("rm", file.pathAsString)))
      a <- fa
      _ <- StateT.modify[IO, MockState](_.exec(List("restore", file.pathAsString)))
    } yield a

  override def readFile(file: File): MockEff[Option[String]] =
    StateT(s => IO.pure((s.exec(List("read", file.pathAsString)), s.files.get(file))))

  override def walk(dir: File): Stream[MockEff, File] = {
    val dirAsString = dir.pathAsString
    val state = StateT { s: MockState =>
      val files = s.files.keys.filter(_.pathAsString.startsWith(dirAsString)).toList
      IO.pure((s, files))
    }
    Stream.eval(state).flatMap(Stream.emits[MockEff, File])
  }

  override def writeFile(file: File, content: String): MockEff[Unit] =
    StateT.modify(_.exec(List("write", file.pathAsString)).add(file, content))
}
