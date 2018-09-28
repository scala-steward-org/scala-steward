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

package eu.timepit.scalasteward.application

import better.files.File
import cats.effect.Sync
import eu.timepit.scalasteward.github.data.Repo

trait WorkspaceAlg[F[_]] {
  def rootDir: F[File]

  def repoDir(repo: Repo): F[File]
}

object WorkspaceAlg {
  def sync[F[_]](workspace: File)(implicit F: Sync[F]): WorkspaceAlg[F] =
    new WorkspaceAlg[F] {
      override def rootDir: F[File] =
        ensureExists(workspace)

      override def repoDir(repo: Repo): F[File] =
        ensureExists(workspace / "repos" / repo.owner / repo.repo)

      def ensureExists(dir: File): F[File] =
        F.delay {
          if (!dir.exists) dir.createDirectories()
          dir
        }
    }
}
