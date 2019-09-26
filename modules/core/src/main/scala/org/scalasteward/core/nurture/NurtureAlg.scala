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
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.{Branch, GitAlg}
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.update.FilterAlg
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
    logger: Logger[F],
    pullRequestRepo: PullRequestRepository[F],
    sbtAlg: SbtAlg[F],
    F: Async[F]
) {
  def nurture(repo: Repo): F[Either[Throwable, Unit]] =
    logger.infoTotalTime(repo.show) {
      logger.attemptLog(util.string.lineLeftRight(s"Nurture ${repo.show}")) {
        for {
          (fork, baseBranch) <- cloneAndSync(repo)
          _ <- updateDependencies(repo, fork, baseBranch)
          _ <- gitAlg.removeClone(repo)
        } yield ()
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
      _ <- logger.info(util.logger.showUpdates(grouped))
      baseSha1 <- gitAlg.latestSha1(repo, baseBranch)
      memoizedGetDependencies <- Async.memoize {
        sbtAlg.getDependencies(repo).handleError(_ => List.empty)
      }
      _ <- grouped.traverse_ { update =>
        val data =
          UpdateData(
            repo,
            fork,
            repoConfig,
            update,
            baseBranch,
            baseSha1,
            git.branchFor(update)
          )
        processUpdate(data, memoizedGetDependencies)
      }
    } yield ()

  def processUpdate(data: UpdateData, getDependencies: F[List[Dependency]]): F[Unit] =
    for {
      _ <- logger.info(s"Process update ${data.update.show}")
      head = vcs.listingBranch(config.vcsType, data.fork, data.update)
      pullRequests <- vcsApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      _ <- pullRequests.headOption match {
        case Some(pr) if pr.isClosed =>
          logger.info(s"PR ${pr.html_url} is closed")
        case Some(pr) if data.repoConfig.updatePullRequests =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data)
        case Some(pr) =>
          logger.info(s"Found PR ${pr.html_url}, but updates are disabled by flag")
        case None =>
          applyNewUpdate(data, getDependencies)
      }
      _ <- pullRequests.headOption.fold(F.unit) { pr =>
        pullRequestRepo.createOrUpdate(data.repo, pr.html_url, data.baseSha1, data.update, pr.state)
      }
    } yield ()

  def applyNewUpdate(data: UpdateData, getDependencies: F[List[Dependency]]): F[Unit] =
    (editAlg.applyUpdate(data.repo, data.update) >> gitAlg.containsChanges(data.repo)).ifM(
      gitAlg.returnToCurrentBranch(data.repo) {
        for {
          _ <- logger.info(s"Create branch ${data.updateBranch.name}")
          _ <- gitAlg.createBranch(data.repo, data.updateBranch)
          _ <- commitAndPush(data)
          _ <- createPullRequest(data, getDependencies)
        } yield ()
      },
      logger.warn("No files were changed")
    )

  def commitAndPush(data: UpdateData): F[Unit] =
    for {
      _ <- logger.info("Commit and push changes")
      _ <- gitAlg.commitAll(data.repo, git.commitMsgFor(data.update))
      _ <- gitAlg.push(data.repo, data.updateBranch)
    } yield ()

  def createPullRequest(data: UpdateData, getDependencies: F[List[Dependency]]): F[Unit] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      dependencies <- getDependencies
      filteredDependencies = dependenciesInUpdates(dependencies, data.update)
      artifactIdToUrl <- coursierAlg.getArtifactIdUrlMapping(filteredDependencies)
      branchCompareUrl <- vcsExtraAlg.getBranchCompareUrl(
        artifactIdToUrl.get(data.update.artifactId),
        data.update
      )
      releaseNoteUrl <- vcsExtraAlg.getReleaseNoteUrl(
        artifactIdToUrl.get(data.update.artifactId),
        data.update
      )
      branchName = vcs.createBranch(config.vcsType, data.fork, data.update)
      requestData = NewPullRequestData.from(
        data,
        branchName,
        artifactIdToUrl,
        branchCompareUrl,
        releaseNoteUrl
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
        dependencies.filter(
          dep => dep.groupId === groupId && artifactIdSet.contains(dep.artifactId)
        )
    }

  def updatePullRequest(data: UpdateData): F[Unit] =
    gitAlg.returnToCurrentBranch(data.repo) {
      for {
        _ <- gitAlg.checkoutBranch(data.repo, data.updateBranch)
        updated <- shouldBeUpdated(data)
        _ <- if (updated) mergeAndApplyAgain(data) else F.unit
      } yield ()
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
    result.flatMap { case (reset, msg) => logger.info(msg).as(reset) }
  }

  def mergeAndApplyAgain(data: UpdateData): F[Unit] =
    for {
      _ <- logger.info(
        s"Merge branch '${data.baseBranch.name}' into ${data.updateBranch.name} and apply again"
      )
      _ <- gitAlg.mergeTheirs(data.repo, data.baseBranch)
      _ <- editAlg.applyUpdate(data.repo, data.update)
      containsChanges <- gitAlg.containsChanges(data.repo)
      _ <- if (containsChanges) commitAndPush(data) else F.unit
    } yield ()
}
