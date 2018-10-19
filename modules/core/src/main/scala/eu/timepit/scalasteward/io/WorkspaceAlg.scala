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
import cats.FlatMap
import cats.implicits._
import eu.timepit.scalasteward.github.data.Repo
import io.chrisdavenport.log4cats.Logger

trait WorkspaceAlg[F[_]] {
  def cleanWorkspace: F[Unit]

  def rootDir: F[File]

  def repoDir(repo: Repo): F[File]
}

object WorkspaceAlg {
  def create[F[_]](
      implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      workspace: File,
      F: FlatMap[F]
  ): WorkspaceAlg[F] =
    new WorkspaceAlg[F] {
      val reposDir: File =
        workspace / "repos"

      override def cleanWorkspace: F[Unit] =
        for {
          _ <- logger.info(s"Clean workspace $workspace")
          _ <- fileAlg.deleteForce(reposDir)
          _ <- rootDir
        } yield ()

      override def rootDir: F[File] =
        fileAlg.ensureExists(workspace)

      override def repoDir(repo: Repo): F[File] =
        fileAlg.ensureExists(reposDir / repo.owner / repo.repo)
    }
}
