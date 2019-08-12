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

import cats.implicits._
import cats.{Functor, Monad}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.data.{Dependency, Update, Version}
import org.scalasteward.core.util.MonadThrowable
import org.scalasteward.core.vcs.data.Repo

import scala.util.matching.Regex

trait ScalafmtAlg[F[_]] {
  def getScalafmtVersion(repo: Repo): F[Option[Version]]

  def editScalafmtConf(repo: Repo, nextVersion: String)(implicit F: MonadThrowable[F]): F[Unit]

  final def getScalafmtUpdate(repo: Repo)(implicit F: Functor[F]): F[Option[Update.Single]] =
    getScalafmtVersion(repo).map(_.flatMap(findScalafmtUpdate))

  final def getScalafmtDependency(repo: Repo)(implicit F: Functor[F]): F[List[Dependency]] =
    getScalafmtUpdate(repo).map {
      case None => List.empty
      case Some(update) =>
        // FIXME: Scala version is required to fetch artifact URL using Coursier
        val scalaVersions = List("2.12", "2.13")
        scalaVersions.map { scalaV =>
          Dependency(
            update.groupId,
            update.artifactId,
            s"${update.artifactId}_${scalaV}",
            update.currentVersion
          )
        }
    }
}

object ScalafmtAlg {
  def create[F[_]](
      implicit
      fileAlg: FileAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): ScalafmtAlg[F] = new ScalafmtAlg[F] {
    override def getScalafmtVersion(repo: Repo): F[Option[Version]] =
      for {
        repoDir <- workspaceAlg.repoDir(repo)
        scalafmtConfFile = repoDir / ".scalafmt.conf"
        fileContent <- fileAlg.readFile(scalafmtConfFile)
      } yield {
        fileContent.flatMap(parseScalafmtConf)
      }

    override def editScalafmtConf(repo: Repo, nextVersion: String)(
        implicit F: MonadThrowable[F]
    ): F[Unit] =
      for {
        repoDir <- workspaceAlg.repoDir(repo)
        scalafmtConfFile = repoDir / ".scalafmt.conf"
        _ <- fileAlg.editFile(
          scalafmtConfFile,
          content => {
            for {
              currentVersion <- parseScalafmtConf(content)
              curVer = Regex.quote(currentVersion.value)
              pattern = s"""(version\\s*=\\s*.*?)${curVer}(.?)"""
              replacer = s"$$1${nextVersion}$$2"
              changed <- Some(content.replaceFirst(pattern, replacer)) if changed =!= content
            } yield changed
          }
        )
      } yield ()
  }
}
