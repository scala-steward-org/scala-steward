/*
 * Copyright 2018-2020 Scala Steward contributors
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
import cats.implicits._
import cats.{Functor, Monad}
import org.scalasteward.core.buildtool.sbt.defaultScalaBinaryVersion
import org.scalasteward.core.data.{Dependency, Version}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.vcs.data.Repo

trait ScalafmtAlg[F[_]] {
  def getScalafmtVersion(repo: Repo): F[Option[Version]]

  final def getScalafmtDependency(repo: Repo)(implicit F: Functor[F]): F[Option[Dependency]] =
    Nested(getScalafmtVersion(repo)).map(scalafmtDependency(defaultScalaBinaryVersion)).value
}

object ScalafmtAlg {
  def create[F[_]](implicit
      fileAlg: FileAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): ScalafmtAlg[F] =
    new ScalafmtAlg[F] {
      override def getScalafmtVersion(repo: Repo): F[Option[Version]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          scalafmtConfFile = repoDir / ".scalafmt.conf"
          fileContent <- fileAlg.readFile(scalafmtConfFile)
        } yield fileContent.flatMap(parseScalafmtConf)
    }
}
