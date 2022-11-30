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

package org.scalasteward.core.edit

import better.files.File
import cats.effect.Concurrent
import cats.syntax.all._
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.{RepoData, Update}
import org.scalasteward.core.edit.EditAttempt.{ScalafixEdit, UpdateEdit}
import org.scalasteward.core.edit.hooks.HookExecutor
import org.scalasteward.core.edit.scalafix.{ScalafixMigration, ScalafixMigrationsFinder}
import org.scalasteward.core.git.{CommitMsg, GitAlg}
import org.scalasteward.core.io.{isSourceFile, FileAlg, WorkspaceAlg}
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
      update: Update.Single,
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
              (preMigrations, postMigrations) = scalafixMigrationsFinder.findMigrations(update)
              preScalafixEdits <-
                if (preMigrations.isEmpty) F.pure(Nil)
                else
                  gitAlg.discardChanges(repo) *>
                    runScalafixMigrations(repo, data.config, preMigrations) <*
                    bumpVersion(update, files)
              _ <- reformatChangedFiles(data)
              updateEdit <- createUpdateEdit(repo, data.config, update)
              postScalafixEdits <- runScalafixMigrations(repo, data.config, postMigrations)
              hooksEdits <- hookExecutor.execPostUpdateHooks(data, update)
            } yield preScalafixEdits ++ updateEdit ++ postScalafixEdits ++ hooksEdits
        }
    }

  private def findFilesContainingCurrentVersion(
      repo: Repo,
      config: RepoConfig,
      update: Update.Single
  ): F[Option[Nel[File]]] =
    workspaceAlg.repoDir(repo).flatMap { repoDir =>
      val fileFilter = isSourceFile(update, config.updates.fileExtensionsOrDefault) _
      val contentFilter = (_: String).contains(update.currentVersion.value)
      fileAlg.findFiles(repoDir, fileFilter, contentFilter).map(Nel.fromList)
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

  private def bumpVersion(update: Update.Single, files: Nel[File]): F[Boolean] = {
    val actions = UpdateHeuristic.all.map { heuristic =>
      logger.info(s"Trying heuristic '${heuristic.name}'") >>
        fileAlg.editFiles(files, heuristic.replaceVersion(update))
    }
    bindUntilTrue[Nel, F](actions)
  }

  private def reformatChangedFiles(data: RepoData): F[Unit] = {
    val reformat =
      data.config.scalafmt.runAfterUpgradingOrDefault && data.cache.dependsOn(List(scalafmtModule))
    F.whenA(reformat) {
      logger.attemptWarn.log_("Reformatting changed files failed") {
        scalafmtAlg.reformatChanged(data.repo)
      }
    }
  }

  private def createUpdateEdit(
      repo: Repo,
      config: RepoConfig,
      update: Update.Single
  ): F[Option[EditAttempt]] = {
    val commitMsg = CommitMsg.replaceVariables(config.commits.messageOrDefault)(update, repo.branch)
    gitAlg.commitAllIfDirty(repo, commitMsg).map(_.map(commit => UpdateEdit(update, commit)))
  }
}
