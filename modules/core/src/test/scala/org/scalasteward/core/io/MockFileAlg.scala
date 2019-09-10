package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import cats.effect.IO
import fs2.Stream
import org.scalasteward.core.mock.{applyPure, MockEff, MockState}

class MockFileAlg extends FileAlg[MockEff] {
  override def createTemporarily[A](file: File, content: String)(fa: MockEff[A]): MockEff[A] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("create", file.pathAsString)))
      a <- fa
      _ <- StateT.modify[IO, MockState](_.exec(List("rm", file.pathAsString)))
    } yield a

  override def deleteForce(file: File): MockEff[Unit] =
    StateT.modify(_.exec(List("rm", "-rf", file.pathAsString)).rm(file))

  override def ensureExists(dir: File): MockEff[File] =
    applyPure(s => (s.exec(List("mkdir", "-p", dir.pathAsString)), dir))

  override def home: MockEff[File] =
    StateT.pure(File.root / "tmp" / "steward")

  override def isRegularFile(file: File): MockEff[Boolean] =
    StateT.pure(true)

  override def removeTemporarily[A](file: File)(fa: MockEff[A]): MockEff[A] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("rm", file.pathAsString)))
      a <- fa
      _ <- StateT.modify[IO, MockState](_.exec(List("restore", file.pathAsString)))
    } yield a

  override def readFile(file: File): MockEff[Option[String]] =
    applyPure(s => (s.exec(List("read", file.pathAsString)), s.files.get(file)))

  override def walk(dir: File): Stream[MockEff, File] = {
    val dirAsString = dir.pathAsString
    val state: MockEff[List[File]] = StateT.inspect {
      _.files.keys.filter(_.pathAsString.startsWith(dirAsString)).toList
    }
    Stream.eval(state).flatMap(Stream.emits[MockEff, File])
  }

  override def writeFile(file: File, content: String): MockEff[Unit] =
    StateT.modify(_.exec(List("write", file.pathAsString)).add(file, content))
}
