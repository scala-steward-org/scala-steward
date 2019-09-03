/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.scalafmt

import better.files.File
import cats.implicits._
import cats.Monad
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.data.Version

trait ScalafmtAlg[F[_]] {
  def getScalafmtVersion(projectDir: File): F[Option[Version]]
}

object ScalafmtAlg {
  def create[F[_]](
      implicit
      fileAlg: FileAlg[F],
      F: Monad[F]
  ): ScalafmtAlg[F] = new ScalafmtAlg[F] {
    override def getScalafmtVersion(projectDir: File): F[Option[Version]] =
      for {
        fileContent <- fileAlg.readFile(projectDir / ".scalafmt.conf")
      } yield {
        fileContent.flatMap(parseScalafmtConf)
      }
  }
}
