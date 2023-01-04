/*
 * Copyright 2018-2022 Scala Steward contributors
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
import cats.FlatMap
import cats.syntax.all._
import org.scalasteward.core.application.Config
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}
import org.typelevel.log4cats.Logger

trait WorkspaceAlg[F[_]] {
  def cleanReposDir: F[Unit]

  def rootDir: F[File]

  def repoDir(repo: Repo): F[File]

  def buildRootDir(buildRoot: BuildRoot): F[File]
}

object WorkspaceAlg {
  def create[F[_]](config: Config)(implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      F: FlatMap[F]
  ): WorkspaceAlg[F] =
    new WorkspaceAlg[F] {
      private val reposDir: File =
        config.workspace / "repos"

      private def mkRepoDir(repo: Repo): File =
        reposDir / repo.owner / repo.repo

      override def cleanReposDir: F[Unit] =
        logger.info(s"Clean $reposDir") >> fileAlg.deleteForce(reposDir)

      override def rootDir: F[File] =
        fileAlg.ensureExists(config.workspace)

      override def repoDir(repo: Repo): F[File] =
        fileAlg.ensureExists(mkRepoDir(repo))

      override def buildRootDir(buildRoot: BuildRoot): F[File] =
        fileAlg.ensureExists(mkRepoDir(buildRoot.repo) / buildRoot.relativePath)
    }
}
