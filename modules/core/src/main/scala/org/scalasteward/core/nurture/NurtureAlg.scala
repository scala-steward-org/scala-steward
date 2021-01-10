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

package org.scalasteward.core.nurture

import cats.Applicative
import cats.effect.BracketThrow
import cats.implicits._
import eu.timepit.refined.types.numeric.NonNegInt
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.scalasteward.core.application.Config
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.ProcessResult.{Created, Ignored, Updated}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.{Branch, Commit, GitAlg}
import org.scalasteward.core.repoconfig.PullRequestUpdateStrategy
import org.scalasteward.core.scalafix.MigrationAlg
import org.scalasteward.core.util.UrlChecker
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.{VCSApiAlg, VCSExtraAlg, VCSRepoAlg}
import org.scalasteward.core.{git, util, vcs}

final class NurtureAlg[F[_]](config: Config)(implicit
    coursierAlg: CoursierAlg[F],
    editAlg: EditAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    migrationAlg: MigrationAlg,
    pullRequestRepository: PullRequestRepository[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsExtraAlg: VCSExtraAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    streamCompiler: Stream.Compiler[F, F],
    urlChecker: UrlChecker[F],
    F: BracketThrow[F]
) {
  def nurture(data: RepoData, fork: RepoOut, updates: List[Update.Single]): F[Unit] =
    for {
      _ <- logger.info(s"Nurture ${data.repo.show}")
      baseBranch <- cloneAndSync(data.repo, fork)
      _ <- updateDependencies(data, fork.repo, baseBranch, updates)
    } yield ()

  def cloneAndSync(repo: Repo, fork: RepoOut): F[Branch] =
    for {
      _ <- gitAlg.cloneExists(repo).ifM(F.unit, vcsRepoAlg.cloneAndSync(repo, fork))
      baseBranch <- vcsApiAlg.parentOrRepo(fork, config.doNotFork).map(_.default_branch)
    } yield baseBranch

  def updateDependencies(
      data: RepoData,
      fork: Repo,
      baseBranch: Branch,
      updates: List[Update.Single]
  ): F[Unit] =
    for {
      _ <- F.unit
      grouped = Update.groupByGroupId(updates)
      _ <- logger.info(util.logger.showUpdates(grouped))
      baseSha1 <- gitAlg.latestSha1(data.repo, baseBranch)
      _ <- NurtureAlg.processUpdates(
        grouped,
        update => {
          val updateData =
            UpdateData(data, fork, update, baseBranch, baseSha1, git.branchFor(update))
          processUpdate(updateData).flatMap {
            case result @ Created(newPrNumber) =>
              closeObsoletePullRequests(updateData, newPrNumber).as[ProcessResult](result)
            case result @ _ => F.pure(result)
          }
        },
        data.config.updates.limit
      )
    } yield ()

  def processUpdate(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(s"Process update ${data.update.show}")
      head = vcs.listingBranch(config.vcsType, data.fork, data.update)
      pullRequests <- vcsApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      result <- pullRequests.headOption match {
        case Some(pr) if pr.isClosed =>
          logger.info(s"PR ${pr.html_url} is closed") >>
            removeRemoteBranch(data.repo, data.updateBranch).as(Ignored)
        case Some(pr) =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data)
        case None =>
          applyNewUpdate(data)
      }
      _ <- pullRequests.headOption.traverse_ { pr =>
        pullRequestRepository.createOrUpdate(
          data.repo,
          pr.html_url,
          data.baseSha1,
          data.update,
          pr.state,
          pr.number
        )
      }
    } yield result

  def closeObsoletePullRequests(data: UpdateData, newNumber: PullRequestNumber): F[Unit] =
    pullRequestRepository.getObsoleteOpenPullRequests(data.repo, data.update).flatMap {
      _.traverse_ { case (oldNumber, oldUrl, oldUpdate) =>
        closeObsoletePullRequest(data.repo, oldUpdate, oldUrl, oldNumber, newNumber)
      }
    }

  private def closeObsoletePullRequest(
      repo: Repo,
      oldUpdate: Update,
      oldUrl: Uri,
      oldNumber: PullRequestNumber,
      newNumber: PullRequestNumber
  ): F[Unit] =
    logger.attemptLogWarn_(s"Closing PR #$oldNumber failed") {
      for {
        _ <- logger.info(s"Closing obsolete PR ${oldUrl.renderString} for ${oldUpdate.show}")
        comment = s"Superseded by ${vcsApiAlg.referencePullRequest(newNumber)}."
        _ <- vcsApiAlg.commentPullRequest(repo, oldNumber, comment)
        _ <- vcsApiAlg.closePullRequest(repo, oldNumber)
        _ <- removeRemoteBranch(repo, git.branchFor(oldUpdate))
        _ <- pullRequestRepository.changeState(repo, oldUrl, PullRequestState.Closed)
      } yield ()
    }

  private def removeRemoteBranch(repo: Repo, branch: Branch): F[Unit] =
    logger.attemptLogWarn_(s"Removing remote branch ${branch.name} failed") {
      gitAlg.removeBranch(repo, branch)
    }

  def applyNewUpdate(data: UpdateData): F[ProcessResult] =
    gitAlg.returnToCurrentBranch(data.repo) {
      val createBranch = logger.info(s"Create branch ${data.updateBranch.name}") >>
        gitAlg.createBranch(data.repo, data.updateBranch)
      editAlg.applyUpdate(data.repo, data.repoConfig, data.update, createBranch).flatMap {
        editCommits =>
          if (editCommits.isEmpty) logger.warn("No commits created").as(Ignored)
          else pushCommits(data, editCommits) >> createPullRequest(data)
      }
    }

  def pushCommits(data: UpdateData, commits: List[Commit]): F[ProcessResult] =
    if (commits.isEmpty) F.pure[ProcessResult](Ignored)
    else
      for {
        _ <- logger.info(s"Push ${commits.length} commit(s)")
        _ <- gitAlg.push(data.repo, data.updateBranch)
      } yield Updated

  def createPullRequest(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      dependenciesWithNextVersion =
        data.update.dependencies.map(_.copy(version = data.update.nextVersion)).toList
      resolvers = data.repoData.cache.dependencyInfos.flatMap(_.resolvers)
      artifactIdToUrl <-
        coursierAlg.getArtifactIdUrlMapping(Scope(dependenciesWithNextVersion, resolvers))
      existingArtifactUrlsList <- artifactIdToUrl.toList.filterA(a => urlChecker.exists(a._2))
      existingArtifactUrlsMap = existingArtifactUrlsList.toMap
      releaseRelatedUrls <-
        existingArtifactUrlsMap
          .get(data.update.mainArtifactId)
          .traverse(vcsExtraAlg.getReleaseRelatedUrls(_, data.update))
      filesWithOldVersion <- gitAlg.findFilesContaining(data.repo, data.update.currentVersion)
      branchName = vcs.createBranch(config.vcsType, data.fork, data.update)
      migrations = migrationAlg.findMigrations(data.update)
      requestData = NewPullRequestData.from(
        data,
        branchName,
        existingArtifactUrlsMap,
        releaseRelatedUrls.getOrElse(List.empty),
        migrations,
        filesWithOldVersion
      )
      pr <- vcsApiAlg.createPullRequest(data.repo, requestData)
      _ <- pullRequestRepository.createOrUpdate(
        data.repo,
        pr.html_url,
        data.baseSha1,
        data.update,
        pr.state,
        pr.number
      )
      _ <- logger.info(s"Created PR ${pr.html_url}")
    } yield Created(pr.number)

  def updatePullRequest(data: UpdateData): F[ProcessResult] =
    if (data.repoConfig.updatePullRequestsOrDefault =!= PullRequestUpdateStrategy.Never)
      gitAlg.returnToCurrentBranch(data.repo) {
        for {
          _ <- gitAlg.checkoutBranch(data.repo, data.updateBranch)
          update <- shouldBeUpdated(data)
          result <- if (update) mergeAndApplyAgain(data) else F.pure[ProcessResult](Ignored)
        } yield result
      }
    else
      logger.info("PR updates are disabled by flag").as(Ignored)

  def shouldBeUpdated(data: UpdateData): F[Boolean] = {
    val result = gitAlg.isMerged(data.repo, data.updateBranch, data.baseBranch).flatMap {
      case true => (false, "PR has been merged").pure[F]
      case false =>
        gitAlg.branchAuthors(data.repo, data.updateBranch, data.baseBranch).flatMap { authors =>
          val distinctAuthors = authors.distinct
          if (distinctAuthors.length >= 2)
            (false, s"PR has commits by ${distinctAuthors.mkString(", ")}").pure[F]
          else if (data.repoConfig.updatePullRequestsOrDefault === PullRequestUpdateStrategy.Always)
            (true, "PR update strategy is set to always").pure[F]
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
        s"Merge branch ${data.baseBranch.name} into ${data.updateBranch.name} and apply again"
      )
      maybeMergeCommit <- gitAlg.mergeTheirs(data.repo, data.baseBranch)
      editCommits <- editAlg.applyUpdate(data.repo, data.repoConfig, data.update)
      result <- pushCommits(data, maybeMergeCommit.toList ++ editCommits)
    } yield result
}

object NurtureAlg {
  def processUpdates[F[_]](
      updates: List[Update],
      updateF: Update => F[ProcessResult],
      updatesLimit: Option[NonNegInt]
  )(implicit streamCompiler: Stream.Compiler[F, F], F: Applicative[F]): F[Unit] =
    updatesLimit match {
      case None => updates.traverse_(updateF)
      case Some(limit) =>
        Stream
          .emits(updates)
          .evalMap(updateF)
          .through(util.takeUntil(0, limit.value) {
            case Ignored    => 0
            case Updated    => 1
            case Created(_) => 1
          })
          .compile
          .drain
    }
}
