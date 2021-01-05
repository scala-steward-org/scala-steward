/*
 * Copyright 2018-2021 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.io

import better.files.File
import cats.MonadThrow
import cats.effect.{Bracket, Resource, Sync}
import cats.syntax.all._
import cats.{Functor, Traverse}
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.apache.commons.io.FileUtils
import org.http4s.Uri
import org.http4s.implicits.http4sLiteralsSyntax
import scala.io.Source

trait FileAlg[F[_]] {
  def deleteForce(file: File): F[Unit]

  def ensureExists(dir: File): F[File]

  def home: F[File]

  def isDirectory(file: File): F[Boolean]

  def isRegularFile(file: File): F[Boolean]

  def removeTemporarily[A](file: File)(fa: F[A]): F[A]

  def readFile(file: File): F[Option[String]]

  def readResource(resource: String): F[String]

  def readUri(uri: Uri): F[String]

  def walk(dir: File): Stream[F, File]

  def writeFile(file: File, content: String): F[Unit]

  final def createTemporarily[A, E](file: File, content: String)(
      fa: F[A]
  )(implicit F: Bracket[F, E]): F[A] = {
    val delete = deleteForce(file)
    val create = writeFile(file, content).onError(_ => delete)
    F.bracket(create)(_ => fa)(_ => delete)
  }

  final def editFile(file: File, edit: String => Option[String])(implicit
      F: MonadThrow[F]
  ): F[Boolean] =
    readFile(file)
      .flatMap(_.flatMap(edit).fold(F.pure(false))(writeFile(file, _).as(true)))
      .adaptError { case t => new Throwable(s"failed to edit $file", t) }

  final def editFiles[G[_]](files: G[File], edit: String => Option[String])(implicit
      F: MonadThrow[F],
      G: Traverse[G]
  ): F[Boolean] =
    files.traverse(editFile(_, edit)).map(_.foldLeft(false)(_ || _))

  final def findFiles(
      dir: File,
      fileFilter: File => Boolean,
      contentFilter: String => Boolean
  )(implicit
      streamCompiler: Stream.Compiler[F, F],
      F: Functor[F]
  ): F[List[File]] =
    walk(dir)
      .evalFilter(isRegularFile)
      .filter(fileFilter)
      .evalFilter(readFile(_).map(_.fold(false)(contentFilter)))
      .compile
      .toList

  final def writeFileData(dir: File, fileData: FileData): F[Unit] =
    writeFile(dir / fileData.name, fileData.content)
}

object FileAlg {
  def create[F[_]](implicit logger: Logger[F], F: Sync[F]): FileAlg[F] =
    new FileAlg[F] {
      override def deleteForce(file: File): F[Unit] =
        F.delay {
          if (file.exists) FileUtils.forceDelete(file.toJava)
          if (file.exists) file.delete()
        }

      override def ensureExists(dir: File): F[File] =
        F.delay {
          if (!dir.exists) dir.createDirectories()
          dir
        }

      override def home: F[File] =
        F.delay(File.home)

      override def isDirectory(file: File): F[Boolean] =
        F.delay(file.isDirectory(File.LinkOptions.noFollow))

      override def isRegularFile(file: File): F[Boolean] =
        F.delay(file.isRegularFile(File.LinkOptions.noFollow))

      override def removeTemporarily[A](file: File)(fa: F[A]): F[A] =
        F.bracket {
          F.delay {
            val copyOptions = File.CopyOptions(overwrite = true)
            if (file.exists) Some(file.moveTo(File.newTemporaryFile())(copyOptions)) else None
          }
        }(_ => fa) {
          case Some(tmpFile) => F.delay(tmpFile.moveTo(file)).void
          case None          => F.unit
        }

      override def readFile(file: File): F[Option[String]] =
        F.delay(if (file.exists) Some(file.contentAsString) else None)

      override def readResource(resource: String): F[String] =
        readSource(Source.fromResource(resource))

      override def readUri(uri: Uri): F[String] = {
        val scheme = uri.scheme.getOrElse(scheme"file")
        val withScheme = uri.copy(scheme = Some(scheme))
        readSource(Source.fromURL(withScheme.renderString))
      }

      private def readSource(source: => Source): F[String] =
        Resource.fromAutoCloseable(F.delay(source)).use(src => F.delay(src.mkString))

      override def walk(dir: File): Stream[F, File] =
        Stream.eval(F.delay(dir.walk())).flatMap(Stream.fromIterator(_))

      override def writeFile(file: File, content: String): F[Unit] =
        logger.debug(s"Write $file") >>
          file.parentOption.fold(F.unit)(ensureExists(_).void) >>
          F.delay(file.write(content)).void
    }
}
