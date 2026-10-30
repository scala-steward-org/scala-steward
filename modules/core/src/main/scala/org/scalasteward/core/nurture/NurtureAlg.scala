/*
 * Copyright 2018-2025 Scala Steward contributors
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
import cats.syntax.all.*
import cats.{Applicative, Id, Order}
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.*
import org.scalasteward.core.data.ProcessResult.{Created, Ignored, Updated}
import org.scalasteward.core.edit.{EditAlg, EditAttempt}
import org.scalasteward.core.forge.ForgeType.GitHub
import org.scalasteward.core.forge.data.*
import org.scalasteward.core.forge.data.NewPullRequestData.{filterLabels, labelsFor}
import org.scalasteward.core.forge.{ForgeApiAlg, ForgeRepoAlg}
import org.scalasteward.core.git.{Branch, Commit, GitAlg}
import org.scalasteward.core.repoconfig.{PullRequestUpdateStrategy, RetractedArtifact}
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
    pullRequestService: PullRequestService[F],
    updateInfoUrlFinder: UpdateInfoUrlFinder[F],
    urlChecker: UrlChecker[F],
    F: Concurrent[F]
) {
  def nurture(data: RepoData, fork: RepoOut, updates: Nel[Update.ForArtifactId]): F[Unit] =
    for {
      _ <- logger.info(s"Nurture ${data.repo.show}")
      baseBranch <- cloneAndSync(data.repo, fork)
      (grouped, notGrouped) = Update.groupByPullRequestGroup(
        data.config.pullRequestsOrDefault.groupingOrDefault,
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
        .through(util.takeUntilMaybe(0, data.config.updatesOrDefault.limit.map(_.value)) {
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
      maybePr <- pullRequestService.findPullRequestForUpdate(data, config.tpe)
      result <- maybePr match {
        case Some(pr) if pr.state.isClosed && data.update.isInstanceOf[Update.Single] =>
          pullRequestService
            .wasClosedAsNoLongerNeeded(data.repo, pr.html_url)
            .ifM[ProcessResult](
              ifTrue = logger.info(
                s"PR ${pr.html_url} was closed because its update had already been applied, " +
                  "but the update is needed again"
              ) >> createNewPullRequest(data),
              ifFalse = logger.info(s"PR ${pr.html_url} is closed") >>
                deleteRemoteBranch(data.repo, data.updateBranch).as(Ignored)
            )
        case Some(pr) if !pr.state.isClosed =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data, pr.number)
        case _ => createNewPullRequest(data)
      }
    } yield result

  private def createNewPullRequest(data: UpdateData): F[ProcessResult] =
    applyNewUpdate(data).flatTap {
      case Created(newPrNumber) => closeObsoletePullRequests(data, newPrNumber)
      case _                    => F.unit
    }

  private def closeObsoletePullRequests(data: UpdateData, newNumber: PullRequestNumber): F[Unit] =
    data.update.on(
      update = pullRequestService
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
      s"Closing obsolete PR ${oldPr.url.renderString} for ${oldPr.update.show} in PR #$newNumber"
    ) {
      forgeApiAlg
        .commentPullRequest(data.repo, oldPr.number, commentForBeingSupersededBy(newNumber)) >>
        isUnmodifiedByOthers(data.repo, data.baseBranch, oldPr)
          .ifM(closeAndDeleteBranch(data.repo, oldPr, closedReason = None), F.unit)
    }

  /** Checks that nobody but Scala Steward has pushed commits to the branch of `oldPr`. */
  private def isUnmodifiedByOthers(
      repo: Repo,
      baseBranch: Branch,
      oldPr: PullRequestData[Id]
  ): F[Boolean] = {
    val oldRemoteBranch = oldPr.updateBranch.withPrefix("origin/")
    gitAlg
      .branchExists(repo, oldRemoteBranch)
      .ifM(gitAlg.branchAuthors(repo, oldRemoteBranch, baseBranch).map(_.size <= 1), true.pure[F])
  }

  private def closeAndDeleteBranch(
      repo: Repo,
      oldPr: PullRequestData[Id],
      closedReason: Option[ClosedReason]
  ): F[Unit] =
    pullRequestService.closePullRequest(repo, oldPr.number, closedReason) >>
      deleteRemoteBranch(repo, oldPr.updateBranch)

  private def commentForBeingSupersededBy(newNumber: PullRequestNumber): String = config.tpe match {
    // If a PR is part of a list element, GitHub renders its title
    case GitHub => s"""|Superseded by
                       |- ${forgeApiAlg.referencePullRequest(newNumber)}
                       |""".stripMargin.trim
    case _ => s"Superseded by ${forgeApiAlg.referencePullRequest(newNumber)}."
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
      pullRequestsConfig = data.repoData.config.pullRequestsOrDefault
      labels =
        filterLabels(allLabels, pullRequestsConfig.includeMatchedLabels)
    } yield NewPullRequestData.from(
      data = data,
      branchName = config.tpe.pullRequestHeadFor(data.fork, data.updateBranch),
      edits = edits,
      artifactIdToUrl = artifactIdToUrl,
      artifactIdToUpdateInfoUrls = artifactIdToUpdateInfoUrls.toMap,
      filesWithOldVersion = filesWithOldVersion,
      addLabels = config.addLabels,
      draft = pullRequestsConfig.draft.contains(true),
      customLabels = pullRequestsConfig.customLabelsOrDefault,
      labels = labels,
      maximumPullRequestLength = config.tpe.maximumPullRequestLength
    )

  private def createPullRequest(data: UpdateData, edits: List[EditAttempt]): F[ProcessResult] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      requestData <- preparePullRequest(data, edits)
      pr <- pullRequestService.createPullRequest(data, requestData)
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
      case true  => (false, "PR has been merged").pure[F]
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

  /** Closes open PRs that are no longer needed because the base branch moved on by other means:
    * their dependency was bumped to the proposed version or a newer one, or it was removed from the
    * build. `pendingUpdates` tell which occurrences of a dependency a PR is still needed for.
    * Failures are logged and swallowed so that they never prevent the repo from being nurtured.
    */
  def closeNoLongerNeededPullRequests(
      data: RepoData,
      fork: RepoOut,
      pendingUpdates: List[Update.ForArtifactId]
  ): F[Unit] =
    if (!data.config.pullRequestsOrDefault.closeNoLongerNeededOrDefault)
      logger.debug("Closing no longer needed PRs is disabled by config")
    else if (data.cache.maybeRepoConfigParsingError.isDefined)
      // the cache is then based on the default config, e.g. only the default build root
      logger.info("Not closing no longer needed PRs because the repo config could not be parsed")
    else if (data.cache.dependencyInfos.forall(_.value.isEmpty))
      // a build Steward can work with always has some dependency; an empty scan is a broken scan
      logger.info("Not closing no longer needed PRs because no dependencies were found")
    else
      logger.attemptWarn.log_("Closing no longer needed PRs failed") {
        // all dependencies of the build, also those Steward cannot update (e.g. the Scala version
        // of the sbt meta-build), because every occurrence counts for "already applied"
        val dependencies = data.cache.dependencyInfos.flatMap(_.value.map(_.dependency))
        pullRequestService
          .findNoLongerNeededOpenPullRequests(
            data.repo,
            dependencies,
            pendingUpdates,
            data.cache.sha1
          )
          .flatMap { noLongerNeeded =>
            // The clone is needed to check whether somebody else has pushed to the PR branches.
            F.whenA(noLongerNeeded.nonEmpty) {
              cloneAndSync(data.repo, fork).flatMap { baseBranch =>
                noLongerNeeded.traverse_ { case (oldPr, reason) =>
                  closeNoLongerNeededPullRequest(data, baseBranch, oldPr, reason)
                }
              }
            }
          }
      }

  private def closeNoLongerNeededPullRequest(
      data: RepoData,
      baseBranch: Branch,
      oldPr: PullRequestData[Id],
      reason: NoLongerNeeded
  ): F[Unit] =
    logger.attemptWarn.label_(
      s"Closing PR ${oldPr.url.renderString} for ${oldPr.update.show} which is no longer needed"
    ) {
      isUnmodifiedByOthers(data.repo, baseBranch, oldPr).ifM(
        // close first, so that a failed close never leaves a comment behind
        ifTrue = closeAndDeleteBranch(data.repo, oldPr, Some(ClosedReason.of(reason))) >>
          forgeApiAlg
            .commentPullRequest(
              data.repo,
              oldPr.number,
              commentForBeingNoLongerNeeded(baseBranch, oldPr.update, reason)
            )
            .void,
        ifFalse = logger.info(
          s"Keeping PR ${oldPr.url.renderString} open because it has commits by others"
        )
      )
    }

  /** The comment for a PR that is closed because it is no longer needed. */
  private[nurture] def commentForBeingNoLongerNeeded(
      baseBranch: Branch,
      update: Update,
      reason: NoLongerNeeded
  ): String = {
    val closing = "so this PR is no longer needed and is closed."
    reason match {
      case NoLongerNeeded.AlreadyApplied(baseDependencies) =>
        val found = baseDependencies.toList
          .groupBy(d => (d.groupId, d.artifactId.name))
          .toList
          .sortBy { case ((groupId, artifactIdName), _) => (groupId.value, artifactIdName) }
          .map { case ((groupId, artifactIdName), dependencies) =>
            val versions = versionsOf(dependencies)
            val label = if (versions.sizeIs == 1) "version" else "versions"
            s"$groupId:$artifactIdName in $label ${joinWithAnd(versions)}"
          }
        val several = found.sizeIs > 1 || baseDependencies.map(_.version).distinct.size > 1
        val comparison =
          if (several) "all of which are the same as or newer than"
          else "which is the same as or newer than"
        val proposed = update.asSingleUpdates.map(_.nextVersion.value).distinct
        s"The base branch `${baseBranch.name}` already contains ${joinWithAnd(found)}, " +
          s"$comparison the ${joinWithAnd(proposed)} this PR proposes, $closing"
      case NoLongerNeeded.DependencyRemoved =>
        val artifacts = update.asSingleUpdates
          .flatMap(u => u.artifactIds.toList.map(a => s"${u.groupId}:${a.name}"))
          .distinct
        s"Scala Steward no longer finds ${joinWithAnd(artifacts)} in the build of " +
          s"`${baseBranch.name}`, $closing"
    }
  }

  private def versionsOf(dependencies: List[Dependency]): List[String] =
    dependencies.map(_.version).distinct.sorted(using Order[Version].toOrdering).map(_.value)

  private def joinWithAnd(items: List[String]): String =
    items.reverse match {
      case Nil                  => ""
      case last :: Nil          => last
      case last :: initReversed => s"${initReversed.reverse.mkString(", ")} and $last"
    }

  def closeRetractedPullRequests(data: RepoData): F[Unit] =
    pullRequestService
      .getRetractedOpenPullRequests(data.repo, data.config.updatesOrDefault.retractedOrDefault)
      .flatMap {
        _.traverse_ { case (oldPr, retractedArtifact) =>
          closeRetractedPullRequest(data, oldPr, retractedArtifact)
        }
      }

  private def closeRetractedPullRequest(
      data: RepoData,
      oldPr: PullRequestData[Id],
      retraction: RetractedArtifact
  ): F[Unit] =
    logger.attemptWarn.label_(
      s"Closing retracted PR ${oldPr.url.renderString} for ${oldPr.update.show} because of '${retraction.reason}'"
    ) {
      for {
        _ <- forgeApiAlg.commentPullRequest(data.repo, oldPr.number, retraction.retractionMsg)
        _ <- pullRequestService.closePullRequest(data.repo, oldPr.number)
        _ <- deleteRemoteBranch(data.repo, oldPr.updateBranch)
      } yield F.unit
    }
}
