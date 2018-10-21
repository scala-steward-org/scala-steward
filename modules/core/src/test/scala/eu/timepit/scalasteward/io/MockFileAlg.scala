package eu.timepit.scalasteward.io

import better.files.File
import cats.data.State
import eu.timepit.scalasteward.MockState.MockEnv

class MockFileAlg extends FileAlg[MockEnv] {
  override def deleteForce(file: File): MockEnv[Unit] =
    State.modify(s => s.exec(List("rm", "-rf", file.pathAsString)))

  override def ensureExists(dir: File): MockEnv[File] =
    State(s => (s.exec(List("mkdir", "-p", dir.pathAsString)), dir))

  override def home: MockEnv[File] =
    State.pure(File.root / "tmp" / "steward")

  override def removeTemporarily[A](file: File)(fa: MockEnv[A]): MockEnv[A] =
    fa

  override def readFile(file: File): MockEnv[Option[String]] =
    State(s => (s.exec(List("read", file.pathAsString)), None))

  override def writeFile(file: File, content: String): MockEnv[Unit] =
    State.modify(_.exec(List("write", file.pathAsString)))
}
