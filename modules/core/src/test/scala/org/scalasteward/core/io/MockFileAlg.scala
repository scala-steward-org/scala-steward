package org.scalasteward.core.io

import better.files.File
import cats.data.Kleisli
import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import org.http4s.Uri
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.{getFlatMapSet, MockContext, MockEff}

class MockFileAlg extends FileAlg[MockEff] {
  override def deleteForce(file: File): MockEff[Unit] =
    Kleisli(getFlatMapSet(_.exec(List("rm", "-rf", file.pathAsString)).rmFile(file)))

  override def ensureExists(dir: File): MockEff[File] =
    Kleisli(_.update(_.exec(List("mkdir", "-p", dir.pathAsString))) >> ioFileAlg.ensureExists(dir))

  override def home: MockEff[File] =
    Kleisli.pure(MockContext.mockRoot)

  override def isDirectory(file: File): MockEff[Boolean] =
    Kleisli(_.update(_.exec(List("test", "-d", file.pathAsString))) >> ioFileAlg.isDirectory(file))

  override def isRegularFile(file: File): MockEff[Boolean] =
    Kleisli {
      _.update(_.exec(List("test", "-f", file.pathAsString))) >> ioFileAlg.isRegularFile(file)
    }

  override def removeTemporarily[A](file: File)(fa: MockEff[A]): MockEff[A] =
    Kleisli { ref =>
      for {
        _ <- ref.update(_.exec(List("rm", file.pathAsString)))
        a <- ioFileAlg.removeTemporarily(file)(fa.run(ref))
        _ <- ref.update(_.exec(List("restore", file.pathAsString)))
      } yield a
    }

  override def readFile(file: File): MockEff[Option[String]] =
    Kleisli(_.update(_.exec(List("read", file.pathAsString))) >> ioFileAlg.readFile(file))

  override def readResource(resource: String): MockEff[String] =
    Kleisli {
      _.update(_.exec(List("read", s"classpath:$resource"))) >> ioFileAlg.readResource(resource)
    }

  override def readUri(uri: Uri): MockEff[String] =
    Kleisli {
      _.updateAndGet(_.exec(List("read", uri.renderString))).flatMap {
        _.uris.get(uri) match {
          case Some(content) => IO.pure(content)
          case None          => IO.raiseError(new Throwable(s"URI $uri not found"))
        }
      }
    }

  override def walk(dir: File): Stream[MockEff, File] =
    Stream.evals(Kleisli.liftF(ioFileAlg.walk(dir).compile.toList.map(_.sortBy(_.pathAsString))))

  override def writeFile(file: File, content: String): MockEff[Unit] =
    Kleisli(getFlatMapSet(_.exec(List("write", file.pathAsString)).addFiles(file -> content)))
}
