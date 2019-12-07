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

package org.scalasteward.core.edit

import better.files.File
import cats.Traverse
import cats.implicits._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.Update
import org.scalasteward.core.io.{isFileSpecificTo, isSourceFile, FileAlg, WorkspaceAlg}
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.util._
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.scalafix.MigrationAlg

final class EditAlg[F[_]](
    implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    sbtAlg: SbtAlg[F],
    streamCompiler: Stream.Compiler[F, F],
    workspaceAlg: WorkspaceAlg[F],
    migrationAlg: MigrationAlg[F],
    F: MonadThrowable[F]
) {
  def applyUpdate(repo: Repo, update: Update): F[Unit] =
    for {
      _ <- applyScalafixMigrations(repo, update).handleErrorWith(e =>
        logger.warn(s"Could not apply ${update.show} : $e")
      )
      repoDir <- workspaceAlg.repoDir(repo)
      files <- fileAlg.findFilesContaining(
        repoDir,
        update.currentVersion,
        f => isSourceFile(f) && isFileSpecificTo(update)(f)
      )
      noFilesFound = logger.warn("No files found that contain the current version")
      _ <- files.toNel.fold(noFilesFound)(applyUpdateTo(_, update))
    } yield ()

  def applyUpdateTo[G[_]: Traverse](files: G[File], update: Update): F[Unit] = {
    val actions = UpdateHeuristic.all.map { heuristic =>
      logger.info(s"Trying heuristic '${heuristic.name}'") >>
        fileAlg.editFiles(files, heuristic.replaceVersion(update))
    }
    bindUntilTrue(actions).void
  }

  def applyScalafixMigrations(repo: Repo, update: Update): F[Unit] =
    migrationAlg.findMigrations(update) >>= { migrationsList =>
      Nel.fromList(migrationsList).fold(F.unit) { migrations =>
        logger.info(s"Applying migrations: $migrations") >> sbtAlg.runMigrations(repo, migrations)
      }
    }
}
