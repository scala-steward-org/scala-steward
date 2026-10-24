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
import org.scalasteward.core.repoconfig.{
  PullRequestFrequency,
  PullRequestsConfig,
  RepoConfig,
  UpdatePattern
}
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

  /** Finds all updates for the repo and which of them need attention right now. */
  def findUpdates(data: RepoData): F[FoundUpdates] = {
    val dependencies = data.cache.dependencyInfos
      .flatMap(_.sequence)
      .collect { case info if !ignoreDependency(info.value) => info.map(_.dependency) }
      .sorted
    findUpdatesNeedingAttention(data, dependencies)
  }

  private def findUpdatesNeedingAttention(
      data: RepoData,
      dependencies: List[Scope.Dependency]
  ): F[FoundUpdates] = {
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
    } yield FoundUpdates(updates1, result)
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
        pullRequestRepository
          .findLatestPullRequest(repo, crossDependency, update.nextVersion)
          .flatMap {
            case None =>
              F.pure(DependencyOutdated(crossDependency, update))
            case Some(pr) if pr.state.isClosed =>
              // A PR that Scala Steward closed itself because the base branch made the update
              // unnecessary does not mean that the update was declined. If it is needed again,
              // e.g. after a revert, it is proposed again.
              pullRequestRepository.wasClosedAsNoLongerNeeded(repo, pr.url).map {
                case true  => DependencyOutdated(crossDependency, update)
                case false => PullRequestClosed(crossDependency, update, pr.url)
              }
            case Some(pr) if pr.baseSha1 === repoCache.sha1 =>
              F.pure(PullRequestUpToDate(crossDependency, update, pr.url))
            case Some(pr) =>
              F.pure(PullRequestOutdated(crossDependency, update, pr.url))
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
    val (pullRequests: PullRequestsConfig, lastPrCreatedAt: Option[Timestamp]) =
      repoConfig.dependencyOverridesOrDefault
        .collectFirstSome { groupRepoConfig =>
          val matchResult = UpdatePattern
            .findMatch(List(groupRepoConfig.dependency), dependencyOutdated.update, include = true)
          Option.when(matchResult.byArtifactId.nonEmpty && matchResult.filteredVersions.nonEmpty)(
            (groupRepoConfig.pullRequests, artifactLastPrCreatedAt)
          )
        }
        .getOrElse((repoConfig.pullRequestsOrDefault, repoLastPrCreatedAt))
    val frequency = pullRequests.frequency.getOrElse(PullRequestFrequency.Asap)

    val dep = dependencyOutdated.crossDependency.head
    val key = s"${dep.groupId}:${dep.artifactId.name}"
    def ignore(why: String) =
      logger.info(s"Ignoring outdated dependency $key $why").as(false)
    pullRequests.allowedHours match {
      case _ if !frequency.onSchedule(now) =>
        ignore(s"according to $frequency")
      case Some(allowed) if !allowed.matches(now) =>
        ignore(s"according to allowed hours ${allowed.render}")
      case _ =>
        lastPrCreatedAt
          .flatMap(frequency.waitingTime(_, now, key, pullRequests.frequencySpread))
          .fold(true.pure[F])(waitingTime => ignore(s"for ${dateTime.showDuration(waitingTime)}"))
    }
  }
}

object PruningAlg {

  /** All updates found for a repo and the subset that needs attention right now, i.e. for which a
    * PR should be created or updated. `all` also contains updates that are held back, e.g. by
    * `pullRequests.frequency`, but not those that are ignored or pinned by config.
    */
  final case class FoundUpdates(
      all: List[Update.ForArtifactId],
      needingAttention: Option[Nel[WithUpdate]]
  )

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
