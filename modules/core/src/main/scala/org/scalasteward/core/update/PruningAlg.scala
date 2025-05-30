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

package org.scalasteward.core.update

import cats.Monad
import cats.implicits.*
import org.scalasteward.core.data.*
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.{PullRequestFrequency, RepoConfig, UpdatePattern}
import org.scalasteward.core.update.PruningAlg.*
import org.scalasteward.core.update.data.UpdateState
import org.scalasteward.core.update.data.UpdateState.*
import org.scalasteward.core.util
import org.scalasteward.core.util.{dateTime, DateTimeAlg, Nel, Timestamp}
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

final class PruningAlg[F[_]](implicit
    dateTimeAlg: DateTimeAlg[F],
    logger: Logger[F],
    pullRequestRepository: PullRequestRepository[F],
    updateAlg: UpdateAlg[F],
    F: Monad[F]
) {
  def needsAttention(data: RepoData): F[Option[Nel[WithUpdate]]] = {
    val dependencies = data.cache.dependencyInfos
      .flatMap(_.sequence)
      .collect { case info if !ignoreDependency(info.value) => info.map(_.dependency) }
      .sorted
    findUpdatesNeedingAttention(data, dependencies)
  }

  private def findUpdatesNeedingAttention(
      data: RepoData,
      dependencies: List[Scope.Dependency]
  ): F[Option[Nel[WithUpdate]]] = {
    val repo = data.repo
    val repoCache = data.cache
    val repoConfig = data.config
    val depsWithoutResolvers = dependencies.map(_.value).distinct
    for {
      _ <- logger.info(s"Find updates for ${repo.show}")
      updates0 <- updateAlg
        .findUpdates(dependencies, repoConfig, None)
        .map(removeOvertakingUpdates(depsWithoutResolvers, _))
      updateStates0 <- findAllUpdateStates(repo, repoCache, depsWithoutResolvers, updates0)
      outdatedDeps = collectOutdatedDependencies(updateStates0)
      res <- {
        if (outdatedDeps.isEmpty) F.pure((updateStates0, updates0))
        else
          for {
            freshUpdates <- ensureFreshUpdates(repoConfig, dependencies, outdatedDeps, updates0)
              .map(removeOvertakingUpdates(depsWithoutResolvers, _))
            freshStates <- findAllUpdateStates(repo, repoCache, depsWithoutResolvers, freshUpdates)
          } yield (freshStates, freshUpdates)
      }
      (updateStates1, updates1) = res
      _ <- logger.info(util.logger.showUpdates(updates1.widen[Update.Single]))
      result <- filterUpdateStates(repo, repoConfig, updateStates1)
    } yield result
  }

  private def ensureFreshUpdates(
      repoConfig: RepoConfig,
      dependencies: List[Scope.Dependency],
      outdatedDeps: List[DependencyOutdated],
      allUpdates: List[Update.ForArtifactId]
  ): F[List[Update.ForArtifactId]] = {
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
      updates: List[Update.ForArtifactId]
  ): F[List[UpdateState]] = {
    val groupedDependencies = CrossDependency.group(dependencies)
    val groupedUpdates = Update.groupByArtifactIdName(updates)
    groupedDependencies.traverse(findUpdateState(repo, repoCache, groupedUpdates))
  }

  private def findUpdateState(
      repo: Repo,
      repoCache: RepoCache,
      updates: List[Update.ForArtifactId]
  )(
      crossDependency: CrossDependency
  ): F[UpdateState] =
    updates.find(UpdateAlg.isUpdateFor(_, crossDependency)) match {
      case None         => F.pure(DependencyUpToDate(crossDependency))
      case Some(update) =>
        pullRequestRepository.findLatestPullRequest(repo, crossDependency, update.nextVersion).map {
          case None =>
            DependencyOutdated(crossDependency, update)
          case Some(pr) if pr.state.isClosed =>
            PullRequestClosed(crossDependency, update, pr.url)
          case Some(pr) if pr.baseSha1 === repoCache.sha1 =>
            PullRequestUpToDate(crossDependency, update, pr.url)
          case Some(pr) =>
            PullRequestOutdated(crossDependency, update, pr.url)
        }
    }

  private def filterUpdateStates(
      repo: Repo,
      repoConfig: RepoConfig,
      updateStates: List[UpdateState]
  ): F[Option[Nel[WithUpdate]]] =
    for {
      now <- dateTimeAlg.currentTimestamp
      repoLastPrCreatedAt <- pullRequestRepository.lastPullRequestCreatedAt(repo)
      lastPullRequestCreatedAtByArtifact <- pullRequestRepository
        .lastPullRequestCreatedAtByArtifact(repo)
      states <- updateStates.traverseFilter[F, WithUpdate] {
        case s: DependencyOutdated =>
          newPullRequestsAllowed(
            s,
            now,
            repoLastPrCreatedAt,
            artifactLastPrCreatedAt =
              lastPullRequestCreatedAtByArtifact.get(s.update.groupId -> s.update.mainArtifactId),
            repoConfig
          ).map(Option.when(_)(s))
        case s: PullRequestOutdated => Option[WithUpdate](s).pure[F]
        case _                      => F.pure(None)
      }
      result <- Nel.fromList(states) match {
        case some @ Some(states) =>
          val lines = util.string.indentLines(states.map(UpdateState.show).sorted)
          logger.info(s"${repo.show} is outdated:\n" + lines).as(some)
        case None =>
          logger.info(s"${repo.show} is up-to-date").as(None)
      }
    } yield result

  private def newPullRequestsAllowed(
      dependencyOutdated: DependencyOutdated,
      now: Timestamp,
      repoLastPrCreatedAt: Option[Timestamp],
      artifactLastPrCreatedAt: Option[Timestamp],
      repoConfig: RepoConfig
  ): F[Boolean] = {
    val (frequencyz: Option[PullRequestFrequency], lastPrCreatedAt: Option[Timestamp]) =
      repoConfig.dependencyOverridesOrDefault
        .collectFirstSome { groupRepoConfig =>
          val matchResult = UpdatePattern
            .findMatch(List(groupRepoConfig.dependency), dependencyOutdated.update, include = true)
          Option.when(matchResult.byArtifactId.nonEmpty && matchResult.filteredVersions.nonEmpty)(
            (groupRepoConfig.pullRequests.frequency, artifactLastPrCreatedAt)
          )
        }
        .getOrElse((repoConfig.pullRequestsOrDefault.frequency, repoLastPrCreatedAt))
    val frequency = frequencyz.getOrElse(PullRequestFrequency.Asap)

    val dep = dependencyOutdated.crossDependency.head
    val ignoring = s"Ignoring outdated dependency ${dep.groupId}:${dep.artifactId.name}"
    if (!frequency.onSchedule(now))
      logger.info(s"$ignoring according to $frequency").as(false)
    else {
      lastPrCreatedAt.flatMap(frequency.waitingTime(_, now)) match {
        case None              => true.pure[F]
        case Some(waitingTime) =>
          val message = s"$ignoring for ${dateTime.showDuration(waitingTime)}"
          logger.info(message).as(false)
      }
    }
  }
}

object PruningAlg {
  def ignoreDependency(info: DependencyInfo): Boolean =
    info.filesContainingVersion.isEmpty ||
      FilterAlg.isDependencyConfigurationIgnored(info.dependency)

  def removeOvertakingUpdates(
      dependencies: List[Dependency],
      updates: List[Update.ForArtifactId]
  ): List[Update.ForArtifactId] =
    updates.filterNot { update =>
      dependencies.exists { dependency =>
        dependency.groupId === update.groupId &&
        dependency.artifactId === update.artifactId && {
          dependency.version > update.currentVersion &&
          dependency.version <= update.nextVersion
        }
      }
    }

  def collectOutdatedDependencies(updateStates: List[UpdateState]): List[DependencyOutdated] =
    updateStates.collect { case state: DependencyOutdated => state }
}
