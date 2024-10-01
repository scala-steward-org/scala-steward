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

package org.scalasteward.core.nurture

import cats.effect.Concurrent
import cats.syntax.all._
import cats.{Applicative, Id}
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.ProcessResult.{Created, Ignored, Updated}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.{EditAlg, EditAttempt}
import org.scalasteward.core.forge.data.NewPullRequestData.{filterLabels, labelsFor}
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.{ForgeApiAlg, ForgeRepoAlg}
import org.scalasteward.core.git.{Branch, Commit, GitAlg}
import org.scalasteward.core.repoconfig.PullRequestUpdateStrategy
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.util.{Nel, UrlChecker}
import org.scalasteward.core.{git, util}
import org.typelevel.log4cats.Logger

final class NurtureAlg[F[_]](config: ForgeCfg)(implicit
    coursierAlg: CoursierAlg[F],
    editAlg: EditAlg[F],
    forgeApiAlg: ForgeApiAlg[F],
    forgeRepoAlg: ForgeRepoAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    pullRequestRepository: PullRequestRepository[F],
    updateInfoUrlFinder: UpdateInfoUrlFinder[F],
    urlChecker: UrlChecker[F],
    F: Concurrent[F]
) {
  def nurture(data: RepoData, fork: RepoOut, updates: Nel[Update.ForArtifactId]): F[Unit] =
    for {
      _ <- logger.info(s"Nurture ${data.repo.show}")
      baseBranch <- cloneAndSync(data.repo, fork)
      (grouped, notGrouped) = Update.groupByPullRequestGroup(
        data.config.pullRequests.grouping,
        updates.toList
      )
      finalUpdates = Update.groupByGroupId(notGrouped) ++ grouped
      _ <- updateDependencies(data, fork.repo, baseBranch, finalUpdates)
    } yield ()

  private def cloneAndSync(repo: Repo, fork: RepoOut): F[Branch] =
    for {
      _ <- gitAlg.cloneExists(repo).ifM(F.unit, forgeRepoAlg.cloneAndSync(repo, fork))
      baseBranch <- forgeApiAlg.parentOrRepo(fork, config.doNotFork).map(_.default_branch)
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
          val updateData = UpdateData(data, fork, update, baseBranch, baseSha1, updateBranch)
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
      _ <- logger.info(s"Process update ${data.update.show}")
      head = config.tpe.pullRequestHeadFor(data.fork, data.updateBranch)
      pullRequests <- forgeApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      result <- pullRequests.headOption match {
        case Some(pr) if pr.state.isClosed && data.update.isInstanceOf[Update.Single] =>
          logger.info(s"PR ${pr.html_url} is closed") >>
            deleteRemoteBranch(data.repo, data.updateBranch).as(Ignored)
        case Some(pr) if !pr.state.isClosed =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data, pr.number)
        case _ =>
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

  private def closeObsoletePullRequests(data: UpdateData, newNumber: PullRequestNumber): F[Unit] =
    data.update.on(
      update = pullRequestRepository
        .getObsoleteOpenPullRequests(data.repo, _)
        .flatMap(_.traverse_(oldPr => closeObsoletePullRequest(data, newNumber, oldPr))),
      // We don't support closing obsolete PRs for `GroupedUpdate`s
      grouped = _ => Applicative[F].unit
    )

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
        comment = s"Superseded by ${forgeApiAlg.referencePullRequest(newNumber)}."
        _ <- forgeApiAlg.commentPullRequest(data.repo, oldPr.number, comment)
        oldRemoteBranch = oldPr.updateBranch.withPrefix("origin/")
        oldBranchExists <- gitAlg.branchExists(data.repo, oldRemoteBranch)
        authors <-
          if (oldBranchExists) gitAlg.branchAuthors(data.repo, oldRemoteBranch, data.baseBranch)
          else List.empty.pure[F]
        _ <- F.whenA(authors.size <= 1) {
          forgeApiAlg.closePullRequest(data.repo, oldPr.number) >>
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
      data.update
        .on(
          update = editAlg.applyUpdate(data.repoData, _, createBranch),
          grouped = createBranch >> _.updates.flatTraverse(editAlg.applyUpdate(data.repoData, _))
        )
        .flatMap { edits =>
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

  private def dependenciesUpdatedWithNextAndCurrentVersion(
      update: Update
  ): List[(Version, Dependency)] =
    update.on(
      u => u.dependencies.map(_.copy(version = u.nextVersion)).tupleLeft(u.currentVersion).toList,
      _.updates.flatMap(dependenciesUpdatedWithNextAndCurrentVersion(_))
    )

  private[nurture] def preparePullRequest(
      data: UpdateData,
      edits: List[EditAttempt]
  ): F[NewPullRequestData] =
    for {
      _ <- F.unit
      dependenciesWithNextVersion = dependenciesUpdatedWithNextAndCurrentVersion(data.update)
      resolvers = data.repoData.cache.dependencyInfos.flatMap(_.resolvers)
      dependencyToMetadata <- dependenciesWithNextVersion
        .traverse { case (_, dependency) =>
          coursierAlg
            .getMetadata(dependency, resolvers)
            .flatMap(_.filterUrls(urlChecker.exists))
            .tupleLeft(dependency)
        }
        .map(_.toMap)
      artifactIdToUrl = dependencyToMetadata.toList.mapFilter { case (dependency, metadata) =>
        metadata.repoUrl.tupleLeft(dependency.artifactId.name)
      }.toMap
      artifactIdToUpdateInfoUrls <- dependenciesWithNextVersion.flatTraverse {
        case (currentVersion, dependency) =>
          dependencyToMetadata.get(dependency).toList.traverse { metadata =>
            updateInfoUrlFinder
              .findUpdateInfoUrls(metadata, Version.Update(currentVersion, dependency.version))
              .tupleLeft(dependency.artifactId.name)
          }
      }
      artifactIdToVersionScheme = dependencyToMetadata.toList.mapFilter {
        case (dependency, metadata) =>
          metadata.versionScheme.tupleLeft(dependency.artifactId.name)
      }.toMap
      filesWithOldVersion <-
        data.update
          .on(u => List(u.currentVersion.value), _.updates.map(_.currentVersion.value))
          .flatTraverse(gitAlg.findFilesContaining(data.repo, _))
          .map(_.distinct)
      allLabels = labelsFor(data.update, edits, filesWithOldVersion, artifactIdToVersionScheme)
      labels = filterLabels(allLabels, data.repoData.config.pullRequests.includeMatchedLabels)
    } yield NewPullRequestData.from(
      data = data,
      branchName = config.tpe.pullRequestHeadFor(data.fork, data.updateBranch),
      edits = edits,
      artifactIdToUrl = artifactIdToUrl,
      artifactIdToUpdateInfoUrls = artifactIdToUpdateInfoUrls.toMap,
      filesWithOldVersion = filesWithOldVersion,
      addLabels = config.addLabels,
      labels = data.repoData.config.pullRequests.customLabels ++ labels
    )

  private def createPullRequest(data: UpdateData, edits: List[EditAttempt]): F[ProcessResult] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      requestData <- preparePullRequest(data, edits)
      pr <- forgeApiAlg.createPullRequest(data.repo, requestData)
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

  private def updatePullRequest(data: UpdateData, number: PullRequestNumber): F[ProcessResult] =
    if (data.repoConfig.updatePullRequestsOrDefault =!= PullRequestUpdateStrategy.Never)
      gitAlg.returnToCurrentBranch(data.repo) {
        gitAlg.checkoutBranch(data.repo, data.updateBranch) >>
          shouldBeUpdated(data).ifM(
            ifTrue = resetAndApplyAgain(number, data),
            ifFalse = (Ignored: ProcessResult).pure
          )
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

  private def resetAndApplyAgain(number: PullRequestNumber, data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(
        s"Reset ${data.updateBranch.name} to ${data.baseBranch.name} and apply again"
      )
      _ <- gitAlg.resetHard(data.repo, data.baseBranch)
      edits <- data.update.on(
        update = editAlg.applyUpdate(data.repoData, _),
        grouped = _.updates.flatTraverse(editAlg.applyUpdate(data.repoData, _))
      )
      editCommits = edits.flatMap(_.commits)
      result <- pushCommits(data, editCommits)
      requestData <- preparePullRequest(data, edits)
      _ <- forgeApiAlg.updatePullRequest(number: PullRequestNumber, data.repo, requestData)
    } yield result
}
