/*
 * Copyright 2018-2023 Scala Steward contributors
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

import cats.MonadThrow
import cats.syntax.all._
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.{Repo, RepoData, Update}
import org.scalasteward.core.edit.EditAttempt.{ScalafixEdit, UpdateEdit}
import org.scalasteward.core.edit.hooks.HookExecutor
import org.scalasteward.core.edit.scalafix.{ScalafixMigration, ScalafixMigrationsFinder}
import org.scalasteward.core.edit.update.data.Substring
import org.scalasteward.core.edit.update.{ScannerAlg, Selector}
import org.scalasteward.core.git.{CommitMsg, GitAlg}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.{scalafmtModule, ScalafmtAlg}
import org.scalasteward.core.util.logger._
import org.typelevel.log4cats.Logger

final class EditAlg[F[_]](implicit
    buildToolDispatcher: BuildToolDispatcher[F],
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    hookExecutor: HookExecutor[F],
    logger: Logger[F],
    scalafixMigrationsFinder: ScalafixMigrationsFinder,
    scalafmtAlg: ScalafmtAlg[F],
    scannerAlg: ScannerAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrow[F]
) {
  def applyUpdate(
      data: RepoData,
      update: Update.Single,
      preCommit: F[Unit] = F.unit
  ): F[List[EditAttempt]] =
    findUpdateReplacements(data.repo, data.config, update).flatMap {
      case Nil => logger.warn(s"Unable to bump version for update ${update.show}").as(Nil)
      case updateReplacements =>
        for {
          _ <- preCommit
          (preMigrations, postMigrations) = scalafixMigrationsFinder.findMigrations(update)
          preScalafixEdits <- runScalafixMigrations(data.repo, data.config, preMigrations)
          // PreUpdate migrations could invalidate previously found replacements,
          // so we find them again if the migrations produced any changes.
          freshReplacements <-
            if (preScalafixEdits.flatMap(_.commits).isEmpty) F.pure(updateReplacements)
            else findUpdateReplacements(data.repo, data.config, update)
          updateEdit <- applyUpdateReplacements(data, update, freshReplacements)
          postScalafixEdits <- runScalafixMigrations(data.repo, data.config, postMigrations)
          hooksEdits <- hookExecutor.execPostUpdateHooks(data, update)
        } yield preScalafixEdits ++ updateEdit ++ postScalafixEdits ++ hooksEdits
    }

  private def findUpdateReplacements(
      repo: Repo,
      config: RepoConfig,
      update: Update.Single
  ): F[List[Substring.Replacement]] =
    for {
      versionPositions <- scannerAlg.findVersionPositions(repo, config, update.currentVersion)
      modulePositions <- scannerAlg.findModulePositions(repo, config, update.dependencies)
    } yield Selector.select(update, versionPositions, modulePositions)

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
      result <- logger.attemptWarn.log("Scalafix migration failed") {
        buildToolDispatcher.runMigration(repo, config, migration)
      }
      maybeCommit <- gitAlg.commitAllIfDirty(repo, migration.commitMessage(result))
    } yield ScalafixEdit(migration, result, maybeCommit)

  private def applyUpdateReplacements(
      data: RepoData,
      update: Update.Single,
      updateReplacements: List[Substring.Replacement]
  ): F[Option[EditAttempt]] =
    for {
      repoDir <- workspaceAlg.repoDir(data.repo)
      replacementsByPath = updateReplacements.groupBy(_.position.path).toList
      _ <- replacementsByPath.traverse { case (path, replacements) =>
        fileAlg.editFile(repoDir / path, Substring.Replacement.applyAll(replacements))
      }
      _ <- reformatChangedFiles(data)
      msgTemplate = data.config.commits.messageOrDefault
      commitMsg = CommitMsg.replaceVariables(msgTemplate)(update, data.repo.branch)
      maybeCommit <- gitAlg.commitAllIfDirty(data.repo, commitMsg)
    } yield maybeCommit.map(UpdateEdit(update, _))

  private def reformatChangedFiles(data: RepoData): F[Unit] = {
    val reformat =
      data.config.scalafmt.runAfterUpgradingOrDefault && data.cache.dependsOn(List(scalafmtModule))
    F.whenA(reformat) {
      data.config.buildRootsOrDefault(data.repo).traverse_ { buildRoot =>
        logger.attemptWarn.log_(s"Reformatting changed files failed in ${buildRoot.relativePath}") {
          scalafmtAlg.reformatChanged(buildRoot)
        }
      }
    }
  }
}
