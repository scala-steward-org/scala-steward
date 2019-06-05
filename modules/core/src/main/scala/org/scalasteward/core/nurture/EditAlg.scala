/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.nurture

import better.files.File
import cats.Traverse
import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.model.Update
import org.scalasteward.core.util._
import org.scalasteward.core.vcs.data.Repo

trait EditAlg[F[_]] {
  def applyUpdate(repo: Repo, update: Update): F[Unit]
}

object EditAlg {
  def create[F[_]](
      implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Sync[F]
  ): EditAlg[F] =
    new EditAlg[F] {
      override def applyUpdate(repo: Repo, update: Update): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          files <- fileAlg.findSourceFilesContaining(repoDir, update.currentVersion)
          noFilesFound = logger.warn("No files found that contain the current version")
          _ <- files.toNel.fold(noFilesFound)(applyUpdateTo(_, update))
        } yield ()

      def applyUpdateTo[G[_]: Traverse](files: G[File], update: Update): F[Unit] = {
        def applyHeuristic(name: String, edit: String => Option[String]): F[Boolean] =
          logger.info(s"Trying heuristic '$name'") >> fileAlg.editFiles(files, edit)

        val heuristics = Nel.of(
          applyHeuristic("strict", update.replaceAllInStrict),
          applyHeuristic("original", update.replaceAllIn),
          applyHeuristic("relaxed", update.replaceAllInRelaxed),
          applyHeuristic("sliding", update.replaceAllInSliding),
          applyHeuristic("groupId", update.replaceAllInGroupId)
        )
        bindUntilTrue(heuristics).void
      }
    }
}
