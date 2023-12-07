package org.scalasteward.core.io

import better.files.File
import cats.data.Kleisli
import cats.effect.{IO, Resource}
import fs2.Stream
import org.http4s.Uri
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock._

class MockFileAlg extends FileAlg[MockEff] {
  override def deleteForce(file: File): MockEff[Unit] =
    Kleisli(getFlatMapSet(_.exec("rm", "-rf", file.pathAsString).rmFile(file)))

  override def ensureExists(dir: File): MockEff[File] =
    Kleisli(_.update(_.exec("mkdir", "-p", dir.pathAsString)) >> ioFileAlg.ensureExists(dir))

  override def isDirectory(file: File): MockEff[Boolean] =
    Kleisli(_.update(_.exec("test", "-d", file.pathAsString)) >> ioFileAlg.isDirectory(file))

  override def isNonEmptyDirectory(dir: File): MockEff[Boolean] =
    Kleisli {
      _.update(_.exec("find", dir.pathAsString, "-type", "d", "-not", "-empty")) >>
        ioFileAlg.isNonEmptyDirectory(dir)
    }

  override def isRegularFile(file: File): MockEff[Boolean] =
    Kleisli(_.update(_.exec("test", "-f", file.pathAsString)) >> ioFileAlg.isRegularFile(file))

  override def removeTemporarily(file: File): Resource[MockEff, Unit] =
    for {
      _ <- Resource.eval(Kleisli((_: MockCtx).update(_.exec("rm", file.pathAsString))))
      _ <- ioFileAlg.removeTemporarily(file).mapK(ioToMockEff)
      _ <- Resource.eval(Kleisli((_: MockCtx).update(_.exec("restore", file.pathAsString))))
    } yield ()

  override def readFile(file: File): MockEff[Option[String]] =
    Kleisli(_.update(_.exec("read", file.pathAsString)) >> ioFileAlg.readFile(file))

  override def readResource(resource: String): MockEff[String] =
    Kleisli {
      _.update(_.exec("read", s"classpath:$resource")) >> ioFileAlg.readResource(resource)
    }

  override def readUri(uri: Uri): MockEff[String] =
    Kleisli {
      _.updateAndGet(_.exec("read", uri.renderString)).flatMap {
        _.uris.get(uri) match {
          case Some(content) => IO.pure(content)
          case None          => IO.raiseError(new Throwable(s"URI $uri not found"))
        }
      }
    }

  override def walk(dir: File, maxDepth: Int): Stream[MockEff, File] =
    Stream.evals(
      Kleisli.liftF(ioFileAlg.walk(dir, maxDepth).compile.toList.map(_.sortBy(_.pathAsString)))
    )

  override def writeFile(file: File, content: String): MockEff[Unit] =
    Kleisli(getFlatMapSet(_.exec("write", file.pathAsString).addFiles(file -> content)))
}
