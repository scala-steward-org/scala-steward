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

package org.scalasteward.core.update

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data._
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.{IncludeScalaStrategy, PullRequestFrequency, RepoConfig}
import org.scalasteward.core.update.PruningAlg._
import org.scalasteward.core.update.data.UpdateState
import org.scalasteward.core.update.data.UpdateState._
import org.scalasteward.core.util
import org.scalasteward.core.util.{dateTime, DateTimeAlg}
import org.scalasteward.core.vcs.data.PullRequestState.Closed
import org.scalasteward.core.vcs.data.Repo
import scala.concurrent.duration._

final class PruningAlg[F[_]](implicit
    dateTimeAlg: DateTimeAlg[F],
    logger: Logger[F],
    pullRequestRepository: PullRequestRepository[F],
    updateAlg: UpdateAlg[F],
    F: Monad[F]
) {
  def needsAttention(data: RepoData): F[(Boolean, List[Update.Single])] = {
    val ignoreScalaDependency =
      data.config.updates.includeScalaOrDefault === IncludeScalaStrategy.No
    val dependencies = data.cache.dependencyInfos
      .flatMap(_.sequence)
      .collect {
        case info if !ignoreDependency(info.value, ignoreScalaDependency) => info.map(_.dependency)
      }
      .sorted
    findUpdatesNeedingAttention(data, dependencies)
  }

  private def findUpdatesNeedingAttention(
      data: RepoData,
      dependencies: List[Scope.Dependency]
  ): F[(Boolean, List[Update.Single])] = {
    val repo = data.repo
    val repoCache = data.cache
    val repoConfig = data.config
    val depsWithoutResolvers = dependencies.map(_.value).distinct
    for {
      _ <- logger.info(s"Find updates for ${repo.show}")
      updates0 <- updateAlg.findUpdates(dependencies, repoConfig, None)
      updateStates0 <- findAllUpdateStates(repo, repoCache, depsWithoutResolvers, updates0)
      outdatedDeps = collectOutdatedDependencies(updateStates0)
      (updateStates1, updates1) <- {
        if (outdatedDeps.isEmpty) F.pure((updateStates0, updates0))
        else
          for {
            freshUpdates <- ensureFreshUpdates(repoConfig, dependencies, outdatedDeps, updates0)
            freshStates <- findAllUpdateStates(repo, repoCache, depsWithoutResolvers, freshUpdates)
          } yield (freshStates, freshUpdates)
      }
      _ <- logger.info(util.logger.showUpdates(updates1.widen[Update]))
      result <- checkUpdateStates(repo, repoConfig, updateStates1)
    } yield result
  }

  private def ensureFreshUpdates(
      repoConfig: RepoConfig,
      dependencies: List[Scope.Dependency],
      outdatedDeps: List[DependencyOutdated],
      allUpdates: List[Update.Single]
  ): F[List[Update.Single]] = {
    val unseenUpdates = outdatedDeps.map(_.update)
    val maybeOutdatedDeps = dependencies.filter { d =>
      unseenUpdates.exists { u =>
        u.groupId === d.value.groupId && u.currentVersion === d.value.version
      }
    }
    val seenUpdates = allUpdates.filterNot { u =>
      maybeOutdatedDeps.exists(_.value === u.crossDependency.head)
    }
    updateAlg.findUpdates(maybeOutdatedDeps, repoConfig, Some(5.minutes)).map(_ ++ seenUpdates)
  }

  private def findAllUpdateStates(
      repo: Repo,
      repoCache: RepoCache,
      dependencies: List[Dependency],
      updates: List[Update.Single]
  ): F[List[UpdateState]] = {
    val groupedDependencies = CrossDependency.group(dependencies)
    val groupedUpdates = Update.groupByArtifactIdName(updates)
    groupedDependencies.traverse(findUpdateState(repo, repoCache, groupedUpdates))
  }

  private def findUpdateState(repo: Repo, repoCache: RepoCache, updates: List[Update.Single])(
      crossDependency: CrossDependency
  ): F[UpdateState] =
    updates.find(UpdateAlg.isUpdateFor(_, crossDependency)) match {
      case None => F.pure(DependencyUpToDate(crossDependency))
      case Some(update) =>
        pullRequestRepository.findLatestPullRequest(repo, crossDependency, update.nextVersion).map {
          case None =>
            DependencyOutdated(crossDependency, update)
          case Some((uri, _, Closed)) =>
            PullRequestClosed(crossDependency, update, uri)
          case Some((uri, baseSha1, _)) if baseSha1 === repoCache.sha1 =>
            PullRequestUpToDate(crossDependency, update, uri)
          case Some((uri, _, _)) =>
            PullRequestOutdated(crossDependency, update, uri)
        }
    }

  private def checkUpdateStates(
      repo: Repo,
      repoConfig: RepoConfig,
      updateStates: List[UpdateState]
  ): F[(Boolean, List[Update.Single])] =
    newPullRequestsAllowed(repo, repoConfig.pullRequests.frequencyOrDefault).flatMap { allowed =>
      val (outdatedStates, updates) = updateStates.collect {
        case s: DependencyOutdated if allowed => (s, s.update)
        case s: PullRequestOutdated           => (s, s.update)
      }.separate
      val isOutdated = outdatedStates.nonEmpty
      val message = if (isOutdated) {
        val states = util.string.indentLines(outdatedStates.map(UpdateState.show).sorted)
        s"${repo.show} is outdated:\n" + states
      } else
        s"${repo.show} is up-to-date"
      logger.info(message).as((isOutdated, updates))
    }

  private def newPullRequestsAllowed(repo: Repo, frequency: PullRequestFrequency): F[Boolean] =
    if (frequency === PullRequestFrequency.Asap) true.pure[F]
    else
      dateTimeAlg.currentTimestamp.flatMap { now =>
        val ignoring = "Ignoring outdated dependencies"
        if (!frequency.onSchedule(now))
          logger.info(s"$ignoring according to $frequency").as(false)
        else {
          val maybeWaitingTime = OptionT(pullRequestRepository.lastPullRequestCreatedAt(repo))
            .subflatMap(frequency.waitingTime(_, now))
          maybeWaitingTime.value.flatMap {
            case None => true.pure[F]
            case Some(waitingTime) =>
              val message = s"$ignoring for ${dateTime.showDuration(waitingTime)}"
              logger.info(message).as(false)
          }
        }
      }
}

object PruningAlg {
  def ignoreDependency(info: DependencyInfo, ignoreScalaDependency: Boolean): Boolean =
    info.filesContainingVersion.isEmpty ||
      FilterAlg.isScalaDependencyIgnored(info.dependency, ignoreScalaDependency) ||
      FilterAlg.isDependencyConfigurationIgnored(info.dependency)

  def collectOutdatedDependencies(updateStates: List[UpdateState]): List[DependencyOutdated] =
    updateStates.collect { case state: DependencyOutdated => state }
}
