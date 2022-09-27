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

package org.scalasteward.core.nurture

import cats.Id
import cats.effect.Concurrent
import cats.implicits._
import org.scalasteward.core.application.Config.VCSCfg
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.ProcessResult.{Created, Ignored, Updated}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.{EditAlg, EditAttempt}
import org.scalasteward.core.git.{Branch, Commit, GitAlg}
import org.scalasteward.core.repoconfig.PullRequestUpdateStrategy
import org.scalasteward.core.util.{Nel, UrlChecker}
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
  def nurture(data: RepoData, fork: RepoOut, updates: Nel[Update.Single]): F[Unit] =
    for {
      _ <- logger.info(s"Nurture ${data.repo.show}")
      baseBranch <- cloneAndSync(data.repo, fork)
      _ <- updateDependencies(data, fork.repo, baseBranch, Update.groupByGroupId(updates.toList))
    } yield ()

  private def cloneAndSync(repo: Repo, fork: RepoOut): F[Branch] =
    for {
      _ <- gitAlg.cloneExists(repo).ifM(F.unit, vcsRepoAlg.cloneAndSync(repo, fork))
      baseBranch <- vcsApiAlg.parentOrRepo(fork, config.doNotFork).map(_.default_branch)
    } yield baseBranch

  private def updateDependencies(
      data: RepoData,
      fork: Repo,
      baseBranch: Branch,
      updates: List[Update]
  ): F[Unit] =
    for {
      _ <- logger.info(util.logger.showUpdates(updates))
      baseSha1 <- gitAlg.latestSha1(data.repo, baseBranch)
      _ <- fs2.Stream
        .emits(updates)
        .evalMap { update =>
          val updateBranch = git.branchFor(update, data.repo.branch)
          val updateData =
            UpdateData(data, fork, update, update, baseBranch, baseSha1, updateBranch)
          processUpdate(updateData)
        }
        .through(util.takeUntilMaybe(0, data.config.updates.limit.map(_.value)) {
          case Ignored    => 0
          case Updated    => 1
          case Created(_) => 1
        })
        .compile
        .drain
    } yield ()

  private def processUpdate(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(s"Process update ${data.oldUpdate.show}")
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
          data.oldUpdate,
          pr.state,
          pr.number,
          data.updateBranch
        )
        pullRequestRepository.createOrUpdate(data.repo, prData)
      }
    } yield result

  private def closeObsoletePullRequests(data: UpdateData, newNumber: PullRequestNumber): F[Unit] =
    pullRequestRepository
      .getObsoleteOpenPullRequests(data.repo, data.oldUpdate)
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
        _ <- F.whenA(authors.size <= 1) {
          vcsApiAlg.closePullRequest(data.repo, oldPr.number) >>
            deleteRemoteBranch(data.repo, oldPr.updateBranch)
        }
      } yield ()
    }

  private def deleteRemoteBranch(repo: Repo, branch: Branch): F[Unit] =
    logger.attemptWarn.log_(s"Deleting remote branch ${branch.name} failed") {
      val remoteBranch = branch.withPrefix("origin/")
      gitAlg.branchExists(repo, remoteBranch).ifM(gitAlg.deleteRemoteBranch(repo, branch), F.unit)
    }

  private def applyNewUpdate(data: UpdateData): F[ProcessResult] =
    gitAlg.returnToCurrentBranch(data.repo) {
      val createBranch = logger.info(s"Create branch ${data.updateBranch.name}") >>
        gitAlg.createBranch(data.repo, data.updateBranch)
      editAlg.applyUpdate(data.repoData, data.oldUpdate, createBranch).flatMap { edits =>
        val editCommits = edits.flatMap(_.commits)
        if (editCommits.isEmpty) logger.warn("No commits created").as(Ignored)
        else
          gitAlg.branchesDiffer(data.repo, data.baseBranch, data.updateBranch).flatMap {
            case true  => pushCommits(data, editCommits) >> createPullRequest(data, edits)
            case false => logger.warn("No diff between base and update branch").as(Ignored)
          }
      }
    }

  private def pushCommits(data: UpdateData, commits: List[Commit]): F[ProcessResult] =
    if (commits.isEmpty) F.pure[ProcessResult](Ignored)
    else
      for {
        _ <- logger.info(s"Push ${commits.length} commit(s)")
        _ <- gitAlg.push(data.repo, data.updateBranch)
      } yield Updated

  private def createPullRequest(data: UpdateData, edits: List[EditAttempt]): F[ProcessResult] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      dependenciesWithNextVersion =
        data.oldUpdate.dependencies.map(_.copy(version = data.oldUpdate.nextVersion)).toList
      resolvers = data.repoData.cache.dependencyInfos.flatMap(_.resolvers)
      artifactIdToUrl <-
        coursierAlg.getArtifactIdUrlMapping(Scope(dependenciesWithNextVersion, resolvers))
      existingArtifactUrlsList <- artifactIdToUrl.toList.filterA(a => urlChecker.exists(a._2))
      existingArtifactUrlsMap = existingArtifactUrlsList.toMap
      releaseRelatedUrls <-
        existingArtifactUrlsMap
          .get(data.oldUpdate.mainArtifactId)
          .traverse(vcsExtraAlg.getReleaseRelatedUrls(_, data.oldUpdate))
      filesWithOldVersion <- gitAlg.findFilesContaining(
        data.repo,
        data.oldUpdate.currentVersion.value
      )
      branchName = vcs.createBranch(config.tpe, data.fork, data.updateBranch)
      requestData = NewPullRequestData.from(
        data,
        branchName,
        edits,
        existingArtifactUrlsMap,
        releaseRelatedUrls.getOrElse(List.empty),
        filesWithOldVersion,
        data.repoData.config.pullRequests.includeMatchedLabels
      )
      pr <- vcsApiAlg.createPullRequest(data.repo, requestData)
      _ <- vcsApiAlg
        .labelPullRequest(data.repo, pr.number, requestData.labels)
        .whenA(config.addLabels && requestData.labels.nonEmpty)
      prData = PullRequestData[Id](
        pr.html_url,
        data.baseSha1,
        data.oldUpdate,
        pr.state,
        pr.number,
        data.updateBranch
      )
      _ <- pullRequestRepository.createOrUpdate(data.repo, prData)
      _ <- logger.info(s"Created PR ${pr.html_url}")
    } yield Created(pr.number)

  private def updatePullRequest(data: UpdateData): F[ProcessResult] =
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

  private def shouldBeUpdated(data: UpdateData): F[Boolean] = {
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

  private def mergeAndApplyAgain(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(
        s"Merge branch ${data.baseBranch.name} into ${data.updateBranch.name} and apply again"
      )
      maybeRevertCommit <- gitAlg.revertChanges(data.repo, data.baseBranch)
      maybeMergeCommit <- gitAlg.mergeTheirs(data.repo, data.baseBranch)
      edits <- editAlg.applyUpdate(data.repoData, data.oldUpdate)
      editCommits = edits.flatMap(_.commits)
      commits = maybeRevertCommit.toList ++ maybeMergeCommit.toList ++ editCommits
      result <- pushCommits(data, commits)
    } yield result
}
