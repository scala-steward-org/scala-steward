package org.scalasteward.core.io

import better.files.File
import cats.data.State
import org.scalasteward.core.MockState
import org.scalasteward.core.MockState.MockEnv
import fs2.Stream

class MockFileAlg extends FileAlg[MockEnv] {
  override def deleteForce(file: File): MockEnv[Unit] =
    State.modify(s => s.exec(List("rm", "-rf", file.pathAsString)))

  override def ensureExists(dir: File): MockEnv[File] =
    State(s => (s.exec(List("mkdir", "-p", dir.pathAsString)), dir))

  override def home: MockEnv[File] =
    State.pure(File.root / "tmp" / "steward")

  override def removeTemporarily[A](file: File)(fa: MockEnv[A]): MockEnv[A] =
    for {
      _ <- State.modify((_: MockState).exec(List("rm", file.pathAsString)))
      a <- fa
      _ <- State.modify((_: MockState).exec(List("restore", file.pathAsString)))
    } yield a

  override def readFile(file: File): MockEnv[Option[String]] =
    State(s => (s.exec(List("read", file.pathAsString)), None))

  override def walk(dir: File): Stream[MockEnv, File] =
    Stream.eval(State.pure(dir))

  override def writeFile(file: File, content: String): MockEnv[Unit] =
    State.modify(_.exec(List("write", file.pathAsString)))
}
