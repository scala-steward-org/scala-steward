/*
 * Copyright 2018-2025 Scala Steward contributors
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
import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.typelevel.log4cats.Logger

trait WorkspaceAlg[F[_]] {
  def removeAnyRunSpecificFiles: F[Unit]

  def rootDir: F[File]

  def runSummaryFile: F[File]

  def repoDir(repo: Repo): F[File]

  def buildRootDir(buildRoot: BuildRoot): F[File]
}

object WorkspaceAlg {

  val RunSummaryFileName: String = "run-summary.md"

  def create[F[_]](config: Config)(implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      F: Monad[F]
  ): WorkspaceAlg[F] =
    new WorkspaceAlg[F] {
      private val reposDir: File =
        config.workspace / "repos"

      private val runSummary: File =
        config.workspace / RunSummaryFileName

      /* We don't want the `ensureExists()` side-effect for these files - here, we only want to delete them,
       * not accidentally re-create them while trying to delete them.
       */
      private val runSpecificFiles: Seq[File] = Seq(runSummary, reposDir)

      private def toFile(repo: Repo): File =
        reposDir / repo.owner / repo.repo

      private def toFile(buildRoot: BuildRoot): File =
        toFile(buildRoot.repo) / buildRoot.relativePath

      override def removeAnyRunSpecificFiles: F[Unit] =
        logger.info(s"Removing any run-specific files") >> runSpecificFiles.traverse_(
          fileAlg.deleteForce
        )

      override def rootDir: F[File] =
        fileAlg.ensureExists(config.workspace)

      override def runSummaryFile: F[File] =
        fileAlg.ensureExists(runSummary.parent).map(_ => runSummary)

      override def repoDir(repo: Repo): F[File] =
        fileAlg.ensureExists(toFile(repo))

      override def buildRootDir(buildRoot: BuildRoot): F[File] =
        fileAlg.ensureExists(toFile(buildRoot))
    }
}
