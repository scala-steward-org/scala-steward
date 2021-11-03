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

package org.scalasteward.core.edit

import better.files.File
import cats.effect.Concurrent
import cats.syntax.all._
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.{RepoData, Update}
import org.scalasteward.core.edit.EditAttempt.{ScalafixEdit, UpdateEdit}
import org.scalasteward.core.edit.hooks.HookExecutor
import org.scalasteward.core.edit.scalafix.{ScalafixMigration, ScalafixMigrationsFinder}
import org.scalasteward.core.git
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.{isSourceFile, FileAlg, WorkspaceAlg}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.{scalafmtModule, ScalafmtAlg}
import org.scalasteward.core.util._
import org.scalasteward.core.util.logger._
import org.scalasteward.core.vcs.data.Repo
import org.typelevel.log4cats.Logger

final class EditAlg[F[_]](implicit
    buildToolDispatcher: BuildToolDispatcher[F],
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    hookExecutor: HookExecutor[F],
    logger: Logger[F],
    scalafixMigrationsFinder: ScalafixMigrationsFinder,
    scalafmtAlg: ScalafmtAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Concurrent[F]
) {
  def applyUpdate(
      data: RepoData,
      update: Update,
      preCommit: F[Unit] = F.unit
  ): F[List[EditAttempt]] =
    findFilesContainingCurrentVersion(data.repo, data.config, update).flatMap {
      case None =>
        logger.warn("No files found that contain the current version").as(Nil)
      case Some(files) =>
        bumpVersion(update, files).flatMap {
          case false => logger.warn("Unable to bump version").as(Nil)
          case true =>
            for {
              _ <- preCommit
              repo = data.repo
              migrations = scalafixMigrationsFinder.findMigrations(update)
              scalafixEdits <-
                if (migrations.isEmpty) F.pure(Nil)
                else
                  gitAlg.discardChanges(repo) *>
                    runScalafixMigrations(repo, data.config, migrations) <*
                    bumpVersion(update, files)
              _ <- reformatChangedFiles(repo, data.cache)
              updateCommitMsg = git.commitMsgFor(update, data.config.commits, data.repo.branch)
              updateEdit <- gitAlg
                .commitAllIfDirty(repo, updateCommitMsg)
                .map(_.map(commit => UpdateEdit(update, commit)))
              hooksEdits <- hookExecutor.execPostUpdateHooks(data, update)
            } yield scalafixEdits ++ updateEdit ++ hooksEdits
        }
    }

  private def findFilesContainingCurrentVersion(
      repo: Repo,
      config: RepoConfig,
      update: Update
  ): F[Option[Nel[File]]] =
    workspaceAlg.repoDir(repo).flatMap { repoDir =>
      val fileFilter = isSourceFile(update, config.updates.fileExtensionsOrDefault) _
      fileAlg.findFiles(repoDir, fileFilter, _.contains(update.currentVersion)).map(Nel.fromList)
    }

  private def runScalafixMigrations(
      repo: Repo,
      config: RepoConfig,
      migrations: List[ScalafixMigration]
  ): F[List[EditAttempt]] =
    migrations.traverse(runScalafixMigration(repo, config, _))

  private def runScalafixMigration(
      repo: Repo,
      config: RepoConfig,
      migration: ScalafixMigration
  ): F[EditAttempt] =
    for {
      _ <- logger.info(s"Running migration $migration")
      result <- logger.attemptWarn.log("Scalafix migration failed")(
        buildToolDispatcher.runMigration(repo, config, migration)
      )
      maybeCommit <- gitAlg.commitAllIfDirty(repo, migration.commitMessage(result))
    } yield ScalafixEdit(migration, result, maybeCommit)

  private def bumpVersion(update: Update, files: Nel[File]): F[Boolean] = {
    val actions = UpdateHeuristic.all.map { heuristic =>
      logger.info(s"Trying heuristic '${heuristic.name}'") >>
        fileAlg.editFiles(files, heuristic.replaceVersion(update))
    }
    bindUntilTrue[Nel, F](actions)
  }

  private def reformatChangedFiles(repo: Repo, cache: RepoCache): F[Unit] =
    if (cache.dependsOn(List(scalafmtModule)))
      logger.attemptWarn.log_("Reformatting changed files failed") {
        scalafmtAlg.reformatChanged(repo)
      }
    else F.unit
}
