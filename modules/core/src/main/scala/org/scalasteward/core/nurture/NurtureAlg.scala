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

package org.scalasteward.core.nurture

import cats.effect.Async
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.ProcessResult.{Ignored, Updated}
import org.scalasteward.core.data.{Dependency, ProcessResult, Update}
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.{Branch, GitAlg}
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.update.FilterAlg
import org.scalasteward.core.scalafix.MigrationAlg
import org.scalasteward.core.util.DateTimeAlg
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.vcs.data.{NewPullRequestData, Repo}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSExtraAlg, VCSRepoAlg}
import org.scalasteward.core.{git, util, vcs}

final class NurtureAlg[F[_]](
    implicit
    config: Config,
    dateTimeAlg: DateTimeAlg[F],
    editAlg: EditAlg[F],
    repoConfigAlg: RepoConfigAlg[F],
    filterAlg: FilterAlg[F],
    gitAlg: GitAlg[F],
    coursierAlg: CoursierAlg[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    vcsExtraAlg: VCSExtraAlg[F],
    migrationAlg: MigrationAlg[F],
    logger: Logger[F],
    pullRequestRepo: PullRequestRepository[F],
    sbtAlg: SbtAlg[F],
    F: Async[F]
) {
  def nurture(repo: Repo): F[Either[Throwable, Unit]] =
    logger.infoTotalTime(repo.show) {
      logger.attemptLog(util.string.lineLeftRight(s"Nurture ${repo.show}")) {
        F.bracket(cloneAndSync(repo)) {
          case (fork, baseBranch) => updateDependencies(repo, fork, baseBranch)
        }(_ => gitAlg.removeClone(repo))
      }
    }

  def cloneAndSync(repo: Repo): F[(Repo, Branch)] =
    for {
      _ <- logger.info(s"Clone and synchronize ${repo.show}")
      repoOut <- vcsApiAlg.createForkOrGetRepo(config, repo)
      _ <- vcsRepoAlg.clone(repo, repoOut)
      parent <- vcsRepoAlg.syncFork(repo, repoOut)
    } yield (repoOut.repo, parent.default_branch)

  def updateDependencies(repo: Repo, fork: Repo, baseBranch: Branch): F[Unit] =
    for {
      _ <- logger.info(s"Find updates for ${repo.show}")
      repoConfig <- repoConfigAlg.readRepoConfigOrDefault(repo)
      updates <- sbtAlg.getUpdatesForRepo(repo)
      filtered <- filterAlg.localFilterMany(repoConfig, updates)
      grouped = Update.group(filtered)
      sorted <- NurtureAlg.sortUpdatesByMigration(grouped)
      _ <- logger.info(util.logger.showUpdates(sorted))
      baseSha1 <- gitAlg.latestSha1(repo, baseBranch)
      memoizedGetDependencies <- Async.memoize {
        sbtAlg.getDependencies(repo).handleError(_ => List.empty)
      }
      _ <- NurtureAlg.processUpdates(
        sorted,
        update =>
          processUpdate(
            UpdateData(repo, fork, repoConfig, update, baseBranch, baseSha1, git.branchFor(update)),
            memoizedGetDependencies
          ),
        repoConfig.updates.limit
      )
    } yield ()

  def processUpdate(data: UpdateData, getDependencies: F[List[Dependency]]): F[ProcessResult] =
    for {
      _ <- logger.info(s"Process update ${data.update.show}")
      head = vcs.listingBranch(config.vcsType, data.fork, data.update)
      pullRequests <- vcsApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      result <- pullRequests.headOption match {
        case Some(pr) if pr.isClosed =>
          logger.info(s"PR ${pr.html_url} is closed") >> F.pure[ProcessResult](Ignored)
        case Some(pr) =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data)
        case None =>
          applyNewUpdate(data, getDependencies)
      }
      _ <- pullRequests.headOption.fold(F.unit) { pr =>
        pullRequestRepo.createOrUpdate(data.repo, pr.html_url, data.baseSha1, data.update, pr.state)
      }
    } yield result

  def applyNewUpdate(data: UpdateData, getDependencies: F[List[Dependency]]): F[ProcessResult] =
    (editAlg.applyUpdate(data.repo, data.update) >> gitAlg.containsChanges(data.repo)).ifM(
      gitAlg.returnToCurrentBranch(data.repo) {
        for {
          _ <- logger.info(s"Create branch ${data.updateBranch.name}")
          _ <- gitAlg.createBranch(data.repo, data.updateBranch)
          _ <- commitAndPush(data)
          _ <- createPullRequest(data, getDependencies)
        } yield Updated
      },
      logger.warn("No files were changed") >> F.pure[ProcessResult](Ignored)
    )

  def commitAndPush(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info("Commit and push changes")
      _ <- gitAlg.commitAll(data.repo, git.commitMsgFor(data.update))
      _ <- gitAlg.push(data.repo, data.updateBranch)
    } yield Updated

  def createPullRequest(data: UpdateData, getDependencies: F[List[Dependency]]): F[Unit] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      dependencies <- getDependencies
      filteredDependencies = dependenciesInUpdates(dependencies, data.update)
      artifactIdToUrl <- coursierAlg.getArtifactIdUrlMapping(filteredDependencies)
      branchCompareUrl <- artifactIdToUrl
        .get(data.update.artifactId)
        .flatTraverse(vcsExtraAlg.getBranchCompareUrl(_, data.update))
      releaseNoteUrl <- artifactIdToUrl
        .get(data.update.artifactId)
        .flatTraverse(vcsExtraAlg.getReleaseNoteUrl(_, data.update))
      branchName = vcs.createBranch(config.vcsType, data.fork, data.update)
      migrations <- migrationAlg.findMigrations(data.update)
      requestData = NewPullRequestData.from(
        data,
        branchName,
        artifactIdToUrl,
        branchCompareUrl,
        releaseNoteUrl,
        migrations
      )
      pr <- vcsApiAlg.createPullRequest(data.repo, requestData)
      _ <- pullRequestRepo.createOrUpdate(
        data.repo,
        pr.html_url,
        data.baseSha1,
        data.update,
        pr.state
      )
      _ <- logger.info(s"Created PR ${pr.html_url}")
    } yield ()

  private def dependenciesInUpdates(
      dependencies: List[Dependency],
      update: Update
  ): List[Dependency] =
    update match {
      case Update.Single(groupId, artifactId, _, _, _, _) =>
        dependencies.filter(dep => dep.groupId === groupId && dep.artifactId === artifactId)
      case Update.Group(groupId, artifactIds, _, _) =>
        val artifactIdSet = artifactIds.toList.toSet
        dependencies.filter(dep => dep.groupId === groupId && artifactIdSet.contains(dep.artifactId)
        )
    }

  def updatePullRequest(data: UpdateData): F[ProcessResult] =
    if (data.repoConfig.updatePullRequests) {
      gitAlg.returnToCurrentBranch(data.repo) {
        for {
          _ <- gitAlg.checkoutBranch(data.repo, data.updateBranch)
          update <- shouldBeUpdated(data)
          result <- if (update) mergeAndApplyAgain(data) else F.pure[ProcessResult](Ignored)
        } yield result
      }
    } else {
      logger.info("PR updates are disabled by flag").as(Ignored)
    }

  def shouldBeUpdated(data: UpdateData): F[Boolean] = {
    val result = gitAlg.isMerged(data.repo, data.updateBranch, data.baseBranch).flatMap {
      case true => (false, "PR has been merged").pure[F]
      case false =>
        gitAlg.branchAuthors(data.repo, data.updateBranch, data.baseBranch).flatMap { authors =>
          val distinctAuthors = authors.distinct
          if (distinctAuthors.length >= 2)
            (false, s"PR has commits by ${distinctAuthors.mkString(", ")}").pure[F]
          else
            gitAlg.hasConflicts(data.repo, data.updateBranch, data.baseBranch).map {
              case true  => (true, s"PR has conflicts with ${data.baseBranch.name}")
              case false => (false, s"PR has no conflict with ${data.baseBranch.name}")
            }
        }
    }
    result.flatMap { case (update, msg) => logger.info(msg).as(update) }
  }

  def mergeAndApplyAgain(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(
        s"Merge branch '${data.baseBranch.name}' into ${data.updateBranch.name} and apply again"
      )
      _ <- gitAlg.mergeTheirs(data.repo, data.baseBranch)
      _ <- editAlg.applyUpdate(data.repo, data.update)
      containsChanges <- gitAlg.containsChanges(data.repo)
      result <- if (containsChanges) commitAndPush(data) else F.pure[ProcessResult](Ignored)
    } yield result
}

object NurtureAlg {
  def processUpdates[F[_]: Async](
      updates: List[Update],
      updateF: Update => F[ProcessResult],
      updatesLimit: Option[Int]
  ): F[Unit] =
    updatesLimit match {
      case None => updates.traverse_(updateF)
      case Some(limit) =>
        fs2.Stream
          .emits(updates)
          .evalMap(updateF)
          .through(util.takeUntil(limit) {
            case Ignored => 0
            case Updated => 1
          })
          .compile
          .drain
    }

  def sortUpdatesByMigration[F[_]: Async](
      updates: List[Update]
  )(implicit migrationAlg: MigrationAlg[F]): F[List[Update]] =
    updates
      .traverse(update => migrationAlg.findMigrations(update).map(update -> _.size))
      .map(_.sortBy { case (_, size) => size }
        .map { case (up, _) => up })
}
