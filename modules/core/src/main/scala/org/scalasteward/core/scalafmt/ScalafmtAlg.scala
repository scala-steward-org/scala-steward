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

package org.scalasteward.core.scalafmt

import cats.data.Nested
import cats.syntax.all._
import cats.{Functor, Monad}
import org.scalasteward.core.data.{Dependency, Version}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot

trait ScalafmtAlg[F[_]] {
  def getScalafmtVersion(buildRoot: BuildRoot): F[Option[Version]]

  def version: F[String]

  final def getScalafmtDependency(buildRoot: BuildRoot)(implicit
      F: Functor[F]
  ): F[Option[Dependency]] =
    Nested(getScalafmtVersion(buildRoot)).map(scalafmtDependency).value
}

object ScalafmtAlg {
  def create[F[_]](implicit
      fileAlg: FileAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      processAlg: ProcessAlg[F],
      F: Monad[F]
  ): ScalafmtAlg[F] =
    new ScalafmtAlg[F] {
      override def getScalafmtVersion(buildRoot: BuildRoot): F[Option[Version]] =
        for {
          buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
          scalafmtConfFile = buildRootDir / ".scalafmt.conf"
          fileContent <- fileAlg.readFile(scalafmtConfFile)
        } yield fileContent.flatMap(parseScalafmtConf)

      override def version: F[String] =
        workspaceAlg.rootDir
          .flatMap(processAlg.exec(Nel.of(scalafmtBinary, "--version"), _))
          .map(_.mkString.trim)
    }
}
