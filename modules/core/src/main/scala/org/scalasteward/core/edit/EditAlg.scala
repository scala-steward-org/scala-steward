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

package org.scalasteward.core.edit

import better.files.File
import cats.syntax.all._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.Update
import org.scalasteward.core.edit.hooks.HookExecutor
import org.scalasteward.core.git
import org.scalasteward.core.git.{Commit, GitAlg}
import org.scalasteward.core.io.{isSourceFile, FileAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafix.{Migration, MigrationAlg}
import org.scalasteward.core.util._
import org.scalasteward.core.vcs.data.Repo

final class EditAlg[F[_]](implicit
    buildToolDispatcher: BuildToolDispatcher[F],
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    hookExecutor: HookExecutor[F],
    logger: Logger[F],
    migrationAlg: MigrationAlg,
    streamCompiler: Stream.Compiler[F, F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrow[F]
) {
  def applyUpdate(repo: Repo, repoConfig: RepoConfig, update: Update): F[List[Commit]] =
    findFilesContainingCurrentVersion(repo, repoConfig, update).flatMap {
      case None =>
        logger.warn("No files found that contain the current version").as(Nil)
      case Some(files) =>
        bumpVersion(update, files).flatMap {
          case false => logger.warn("Unable to bump version").as(Nil)
          case true =>
            val migrations = migrationAlg.findMigrations(update)
            for {
              cs1 <-
                if (migrations.isEmpty) F.pure(Nil)
                else
                  gitAlg.discardChanges(repo) *>
                    runScalafixMigrations(repo, migrations) <*
                    bumpVersion(update, files)
              cs2 <- gitAlg.commitAllIfDirty(repo, git.commitMsgFor(update, repoConfig.commits))
              cs3 <- hookExecutor.execPostUpdateHooks(repo, repoConfig, update)
            } yield cs1 ++ cs2 ++ cs3
        }
    }

  private def findFilesContainingCurrentVersion(
      repo: Repo,
      repoConfig: RepoConfig,
      update: Update
  ): F[Option[Nel[File]]] =
    workspaceAlg.repoDir(repo).flatMap { repoDir =>
      val fileFilter = isSourceFile(update, repoConfig.updates.fileExtensionsOrDefault) _
      fileAlg.findFiles(repoDir, fileFilter, _.contains(update.currentVersion)).map(Nel.fromList)
    }

  private def runScalafixMigrations(repo: Repo, migrations: List[Migration]): F[List[Commit]] =
    migrations.traverseFilter { migration =>
      logger.info(s"Running migration $migration") >>
        buildToolDispatcher.runMigrations(repo, Nel.one(migration)).attempt.flatMap {
          case Right(_) =>
            val msg1 = s"Run Scalafix rule(s) ${migration.rewriteRules.mkString_(", ")}"
            val msg2 = migration.doc.map(url => s"See $url for details").toList
            gitAlg.commitAllIfDirty(repo, msg1, msg2: _*)
          case Left(throwable) =>
            logger.error(throwable)("Scalafix migration failed").as(None)
        }
    }

  private def bumpVersion(update: Update, files: Nel[File]): F[Boolean] = {
    val actions = UpdateHeuristic.all.map { heuristic =>
      logger.info(s"Trying heuristic '${heuristic.name}'") >>
        fileAlg.editFiles(files, heuristic.replaceVersion(update))
    }
    bindUntilTrue(actions)
  }
}
