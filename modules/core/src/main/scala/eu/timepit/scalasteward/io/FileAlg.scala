/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.io

import better.files.File
import cats.effect.Sync
import cats.implicits._

trait FileAlg[F[_]] {
  def deleteForce(file: File): F[Unit]

  def removeTemporarily[A](file: File)(fa: F[A]): F[A]

  def readFile(file: File): F[Option[String]]

  def writeFile(file: File, content: String): F[Unit]
}

object FileAlg {
  def sync[F[_]](implicit F: Sync[F]): FileAlg[F] =
    new FileAlg[F] {
      override def deleteForce(file: File): F[Unit] =
        F.delay(if (file.exists) file.delete())

      override def removeTemporarily[A](file: File)(fa: F[A]): F[A] =
        F.bracket {
          F.delay {
            if (file.exists) Some(file.moveTo(File.newTemporaryFile(), true))
            else None
          }
        } { _ =>
          fa
        } {
          case Some(tmpFile) => F.delay(tmpFile.moveTo(file)).void
          case None          => F.unit
        }

      override def readFile(file: File): F[Option[String]] =
        F.delay(if (file.exists) Some(file.contentAsString) else None)

      override def writeFile(file: File, content: String): F[Unit] =
        F.delay(file.write(content)).void
    }
}
