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

import cats.Id
import cats.effect.Concurrent
import cats.implicits._
import eu.timepit.refined.types.numeric.NonNegInt
import fs2.Stream
import org.scalasteward.core.application.Config.VCSCfg
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.ProcessResult.{Created, Ignored, Updated}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.{EditAlg, EditAttempt}
import org.scalasteward.core.git.{Branch, Commit, GitAlg}
import org.scalasteward.core.repoconfig.PullRequestUpdateStrategy
import org.scalasteward.core.util.UrlChecker
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.{VCSApiAlg, VCSExtraAlg, VCSRepoAlg}
import org.scalasteward.core.{git, util, vcs}
import org.typelevel.log4cats.Logger

final class NurtureAlg[F[_]](config: VCSCfg)(implicit
    coursierAlg: CoursierAlg[F],
    editAlg: EditAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    pullRequestRepository: PullRequestRepository[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsExtraAlg: VCSExtraAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    urlChecker: UrlChecker[F],
    F: Concurrent[F]
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
          val updateBranch = git.branchFor(update, data.repo.branch)
          val updateData = UpdateData(data, fork, update, baseBranch, baseSha1, updateBranch)
          processUpdate(updateData)
        },
        data.config.updates.limit
      )
    } yield ()

  def processUpdate(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(s"Process update ${data.update.show}")
      head = vcs.listingBranch(config.tpe, data.fork, data.updateBranch)
      pullRequests <- vcsApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      result <- pullRequests.headOption match {
        case Some(pr) if pr.state.isClosed =>
          logger.info(s"PR ${pr.html_url} is closed") >>
            deleteRemoteBranch(data.repo, data.updateBranch).as(Ignored)
        case Some(pr) =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data)
        case None =>
          applyNewUpdate(data).flatTap {
            case Created(newPrNumber) => closeObsoletePullRequests(data, newPrNumber)
            case _                    => F.unit
          }
      }
      _ <- pullRequests.headOption.traverse_ { pr =>
        val prData = PullRequestData[Id](
          pr.html_url,
          data.baseSha1,
          data.update,
          pr.state,
          pr.number,
          data.updateBranch
        )
        pullRequestRepository.createOrUpdate(data.repo, prData)
      }
    } yield result

  def closeObsoletePullRequests(data: UpdateData, newNumber: PullRequestNumber): F[Unit] =
    pullRequestRepository
      .getObsoleteOpenPullRequests(data.repo, data.update)
      .flatMap(_.traverse_(oldPr => closeObsoletePullRequest(data, newNumber, oldPr)))

  private def closeObsoletePullRequest(
      data: UpdateData,
      newNumber: PullRequestNumber,
      oldPr: PullRequestData[Id]
  ): F[Unit] =
    logger.attemptWarn.label_(
      s"Closing obsolete PR ${oldPr.url.renderString} for ${oldPr.update.show}"
    ) {
      for {
        _ <- pullRequestRepository.changeState(data.repo, oldPr.url, PullRequestState.Closed)
        comment = s"Superseded by ${vcsApiAlg.referencePullRequest(newNumber)}."
        _ <- vcsApiAlg.commentPullRequest(data.repo, oldPr.number, comment)
        oldRemoteBranch = oldPr.updateBranch.withPrefix("origin/")
        oldBranchExists <- gitAlg.branchExists(data.repo, oldRemoteBranch)
        authors <-
          if (oldBranchExists) gitAlg.branchAuthors(data.repo, oldRemoteBranch, data.baseBranch)
          else List.empty.pure[F]
        _ <-
          if (authors.size <= 1) for {
            _ <- vcsApiAlg.closePullRequest(data.repo, oldPr.number)
            _ <- deleteRemoteBranch(data.repo, oldPr.updateBranch)
          } yield ()
          else F.unit
      } yield ()
    }

  private def deleteRemoteBranch(repo: Repo, branch: Branch): F[Unit] =
    logger.attemptWarn.log_(s"Deleting remote branch ${branch.name} failed") {
      val remoteBranch = branch.withPrefix("origin/")
      gitAlg.branchExists(repo, remoteBranch).ifM(gitAlg.deleteRemoteBranch(repo, branch), F.unit)
    }

  def applyNewUpdate(data: UpdateData): F[ProcessResult] =
    gitAlg.returnToCurrentBranch(data.repo) {
      val createBranch = logger.info(s"Create branch ${data.updateBranch.name}") >>
        gitAlg.createBranch(data.repo, data.updateBranch)
      editAlg.applyUpdate(data.repoData, data.update, createBranch).flatMap { edits =>
        val editCommits = edits.flatMap(_.maybeCommit)
        if (editCommits.isEmpty) logger.warn("No commits created").as(Ignored)
        else
          gitAlg.branchesDiffer(data.repo, data.baseBranch, data.updateBranch).flatMap {
            case true  => pushCommits(data, editCommits) >> createPullRequest(data, edits)
            case false => logger.warn("No diff between base and update branch").as(Ignored)
          }
      }
    }

  def pushCommits(data: UpdateData, commits: List[Commit]): F[ProcessResult] =
    if (commits.isEmpty) F.pure[ProcessResult](Ignored)
    else
      for {
        _ <- logger.info(s"Push ${commits.length} commit(s)")
        _ <- gitAlg.push(data.repo, data.updateBranch)
      } yield Updated

  def createPullRequest(data: UpdateData, edits: List[EditAttempt]): F[ProcessResult] =
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
      branchName = vcs.createBranch(config.tpe, data.fork, data.updateBranch)
      requestData = NewPullRequestData.from(
        data,
        branchName,
        edits,
        existingArtifactUrlsMap,
        releaseRelatedUrls.getOrElse(List.empty),
        filesWithOldVersion
      )
      pr <- vcsApiAlg.createPullRequest(data.repo, requestData)
      prData = PullRequestData[Id](
        pr.html_url,
        data.baseSha1,
        data.update,
        pr.state,
        pr.number,
        data.updateBranch
      )
      _ <- pullRequestRepository.createOrUpdate(data.repo, prData)
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
          if (authors.length >= 2)
            (false, s"PR has commits by ${authors.mkString(", ")}").pure[F]
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
      edits <- editAlg.applyUpdate(data.repoData, data.update)
      editCommits = edits.flatMap(_.maybeCommit)
      result <- pushCommits(data, maybeMergeCommit.toList ++ editCommits)
    } yield result
}

object NurtureAlg {
  def processUpdates[F[_]](
      updates: List[Update],
      updateF: Update => F[ProcessResult],
      updatesLimit: Option[NonNegInt]
  )(implicit F: Concurrent[F]): F[Unit] =
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
