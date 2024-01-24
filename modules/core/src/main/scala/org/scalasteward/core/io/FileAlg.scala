/*
 * Copyright 2018-2023 Scala Steward contributors
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
import cats.effect.{Resource, Sync}
import cats.syntax.all._
import cats.{ApplicativeError, MonadThrow}
import fs2.Stream
import org.apache.commons.io.FileUtils
import org.http4s.Uri
import org.http4s.implicits._
import org.typelevel.log4cats.Logger
import scala.io.Source

trait FileAlg[F[_]] {
  def deleteForce(file: File): F[Unit]

  def ensureExists(dir: File): F[File]

  def isDirectory(file: File): F[Boolean]

  def isNonEmptyDirectory(dir: File): F[Boolean]

  def isRegularFile(file: File): F[Boolean]

  def removeTemporarily(file: File): Resource[F, Unit]

  def readFile(file: File): F[Option[String]]

  def readResource(resource: String): F[String]

  def readUri(uri: Uri): F[String]

  def walk(dir: File, maxDepth: Int = Int.MaxValue): Stream[F, File]

  def writeFile(file: File, content: String): F[Unit]

  final def createTemporarily[E](file: File, content: String)(implicit
      F: ApplicativeError[F, E]
  ): Resource[F, Unit] = {
    val delete = deleteForce(file)
    val create = writeFile(file, content).onError(_ => delete)
    Resource.make(create)(_ => delete)
  }

  final def createTemporarily[E](dir: File, data: FileData)(implicit
      F: ApplicativeError[F, E]
  ): Resource[F, Unit] =
    createTemporarily(dir / data.path, data.content)

  final def editFile(file: File, edit: String => F[String])(implicit F: MonadThrow[F]): F[Unit] =
    readFile(file)
      .flatMap(_.fold(F.unit)(edit(_).flatMap(writeFile(file, _))))
      .adaptError { case t => new Throwable(s"failed to edit $file", t) }
}

object FileAlg {
  def create[F[_]](implicit logger: Logger[F], F: Sync[F]): FileAlg[F] =
    new FileAlg[F] {
      override def deleteForce(file: File): F[Unit] =
        F.blocking {
          if (file.exists) FileUtils.forceDelete(file.toJava)
          if (file.exists) file.delete()
        }

      override def ensureExists(dir: File): F[File] =
        F.blocking {
          if (!dir.exists) dir.createDirectories()
          dir
        }

      override def isDirectory(file: File): F[Boolean] =
        F.blocking(file.isDirectory(File.LinkOptions.noFollow))

      override def isNonEmptyDirectory(dir: File): F[Boolean] =
        F.blocking(dir.isDirectory(File.LinkOptions.noFollow) && dir.nonEmpty)

      override def isRegularFile(file: File): F[Boolean] =
        F.blocking(file.isRegularFile(File.LinkOptions.noFollow))

      override def removeTemporarily(file: File): Resource[F, Unit] =
        Resource.make {
          F.blocking {
            val copyOptions = File.CopyOptions(overwrite = true)
            Option.when(file.exists)(file.moveTo(File.newTemporaryFile())(copyOptions))
          }
        } {
          case Some(tmpFile) => F.blocking(tmpFile.moveTo(file)).void
          case None          => F.unit
        }.void

      override def readFile(file: File): F[Option[String]] =
        F.blocking(Option.when(file.exists)(file.contentAsString))

      override def readResource(resource: String): F[String] =
        readSource(F.blocking(Source.fromResource(resource)))

      override def readUri(uri: Uri): F[String] = {
        val scheme = uri.scheme.getOrElse(scheme"file")
        val withScheme = uri.copy(scheme = Some(scheme))
        readSource(F.blocking(Source.fromURL(withScheme.renderString)))
      }

      private def readSource(source: F[Source]): F[String] =
        Resource.fromAutoCloseable(source).use(src => F.blocking(src.mkString))

      override def walk(dir: File, maxDepth: Int): Stream[F, File] =
        Stream.eval(F.delay(dir.walk(maxDepth))).flatMap(Stream.fromBlockingIterator(_, 1))

      override def writeFile(file: File, content: String): F[Unit] =
        logger.debug(s"Write $file") >>
          file.parentOption.fold(F.unit)(ensureExists(_).void) >>
          F.blocking(file.write(content)).void
    }
}
