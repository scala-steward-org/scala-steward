package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import org.http4s.Uri
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.{MockEff, MockState}

class MockFileAlg extends FileAlg[MockEff] {
  override def deleteForce(file: File): MockEff[Unit] =
    StateT.modify[IO, MockState](_.exec(List("rm", "-rf", file.pathAsString)).rm(file)) >>
      StateT.liftF(ioFileAlg.deleteForce(file))

  override def ensureExists(dir: File): MockEff[File] =
    StateT.modify[IO, MockState](_.exec(List("mkdir", "-p", dir.pathAsString))) >>
      StateT.liftF(ioFileAlg.ensureExists(dir))

  override def home: MockEff[File] =
    StateT.pure(File.temp / "scala-steward")

  override def isDirectory(file: File): MockEff[Boolean] =
    StateT.modify[IO, MockState](_.exec(List("test", "-d", file.pathAsString))) >>
      StateT.liftF(ioFileAlg.isDirectory(file))

  override def isRegularFile(file: File): MockEff[Boolean] =
    StateT.modify[IO, MockState](_.exec(List("test", "-f", file.pathAsString))) >>
      StateT.liftF(ioFileAlg.isRegularFile(file))

  override def removeTemporarily[A](file: File)(fa: MockEff[A]): MockEff[A] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("rm", file.pathAsString)))
      s1 <- StateT.get[IO, MockState]
      (s2, a) <- StateT.liftF(ioFileAlg.removeTemporarily(file)(fa.run(s1)))
      _ <- StateT.set[IO, MockState](s2)
      _ <- StateT.modify[IO, MockState](_.exec(List("restore", file.pathAsString)))
    } yield a

  override def readFile(file: File): MockEff[Option[String]] =
    StateT.modify[IO, MockState](_.exec(List("read", file.pathAsString))) >>
      StateT.liftF(ioFileAlg.readFile(file))

  override def readResource(resource: String): MockEff[String] =
    StateT.modify[IO, MockState](_.exec(List("read", s"classpath:$resource"))) >>
      StateT.liftF(ioFileAlg.readResource(resource))

  override def readUri(uri: Uri): MockEff[String] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("read", uri.renderString)))
      s <- StateT.get[IO, MockState]
      content <- StateT.liftF[IO, MockState, String](s.uris.get(uri) match {
        case Some(content) => IO.pure(content)
        case None          => IO.raiseError(new Throwable("URL not found"))
      })
    } yield content

  override def walk(dir: File): Stream[MockEff, File] =
    ioFileAlg.walk(dir).translate(StateT.liftK[IO, MockState])

  override def writeFile(file: File, content: String): MockEff[Unit] =
    StateT.modify[IO, MockState](_.exec(List("write", file.pathAsString)).add(file, content)) >>
      StateT.liftF(ioFileAlg.writeFile(file, content))
}
