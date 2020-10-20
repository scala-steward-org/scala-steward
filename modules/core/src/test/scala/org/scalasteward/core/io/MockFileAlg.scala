package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import cats.effect.IO
import fs2.Stream
import org.http4s.Uri
import org.scalasteward.core.mock.{applyPure, MockEff, MockState}

class MockFileAlg extends FileAlg[MockEff] {
  override def deleteForce(file: File): MockEff[Unit] =
    StateT.modify(_.exec(List("rm", "-rf", file.pathAsString)).rm(file))

  override def ensureExists(dir: File): MockEff[File] =
    applyPure(s => (s.exec(List("mkdir", "-p", dir.pathAsString)), dir))

  override def home: MockEff[File] =
    StateT.pure(File.root / "tmp" / "steward")

  override def isDirectory(file: File): MockEff[Boolean] =
    StateT.pure(false)

  override def isRegularFile(file: File): MockEff[Boolean] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("test", "-f", file.pathAsString)))
      s <- StateT.get[IO, MockState]
      exists = s.files.contains(file)
    } yield exists

  override def removeTemporarily[A](file: File)(fa: MockEff[A]): MockEff[A] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("rm", file.pathAsString)))
      a <- fa
      _ <- StateT.modify[IO, MockState](_.exec(List("restore", file.pathAsString)))
    } yield a

  override def readFile(file: File): MockEff[Option[String]] =
    applyPure(s => (s.exec(List("read", file.pathAsString)), s.files.get(file)))

  override def readResource(resource: String): MockEff[String] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("read", s"classpath:$resource")))
      content <- StateT.liftF(FileAlgTest.ioFileAlg.readResource(resource))
    } yield content

  override def readUri(uri: Uri): MockEff[String] =
    for {
      _ <- StateT.modify[IO, MockState](_.exec(List("read", uri.renderString)))
      s <- StateT.get[IO, MockState]
      content <- StateT.liftF[IO, MockState, String](s.uris.get(uri) match {
        case Some(content) => IO.pure(content)
        case None          => IO.raiseError(new Throwable("URL not found"))
      })
    } yield content

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
