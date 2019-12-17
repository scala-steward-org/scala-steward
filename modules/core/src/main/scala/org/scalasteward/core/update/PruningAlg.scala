/*
 * Copyright 2018-2020 Scala Steward contributors
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
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.{CrossDependency, Dependency, Update}
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.repocache.{RepoCache, RepoCacheRepository}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.update.data.UpdateState
import org.scalasteward.core.update.data.UpdateState._
import org.scalasteward.core.util
import org.scalasteward.core.vcs.data.PullRequestState.Closed
import org.scalasteward.core.vcs.data.Repo

final class PruningAlg[F[_]](
    implicit
    logger: Logger[F],
    pullRequestRepo: PullRequestRepository[F],
    repoCacheRepository: RepoCacheRepository[F],
    sbtAlg: SbtAlg[F],
    updateAlg: UpdateAlg[F],
    F: Monad[F]
) {
  def needsAttention(repo: Repo): F[Boolean] =
    repoCacheRepository.findCache(repo).flatMap {
      case None => F.pure(false)
      case Some(repoCache) =>
        val dependencies = repoCache.dependencyInfos
          .collect { case info if info.filesContainingVersion.nonEmpty => info.dependency }
          .filterNot(FilterAlg.isIgnoredGlobally)
          .sorted
        val repoConfig = repoCache.maybeRepoConfig.getOrElse(RepoConfig.default)
        for {
          resolvers <- sbtAlg.getResolvers(repo)
          updates <- updateAlg.findUpdates(dependencies, repoConfig, resolvers)
          updateStates <- findAllUpdateStates(repo, repoCache, dependencies, updates)
          attentionNeeded <- checkUpdateStates(repo, updateStates)
        } yield attentionNeeded
    }

  private def findAllUpdateStates(
      repo: Repo,
      repoCache: RepoCache,
      dependencies: List[Dependency],
      updates: List[Update.Single]
  ): F[List[UpdateState]] =
    CrossDependency.group(dependencies).traverse(findUpdateState(repo, repoCache, updates))

  private def findUpdateState(repo: Repo, repoCache: RepoCache, updates: List[Update.Single])(
      crossDependency: CrossDependency
  ): F[UpdateState] =
    updates.find(UpdateAlg.isUpdateFor(_, crossDependency)) match {
      case None => F.pure(DependencyUpToDate(crossDependency))
      case Some(update) =>
        pullRequestRepo.findPullRequest(repo, crossDependency, update.nextVersion).map {
          case None =>
            DependencyOutdated(crossDependency, update)
          case Some((uri, _, state)) if state === Closed =>
            PullRequestClosed(crossDependency, update, uri)
          case Some((uri, baseSha1, _)) if baseSha1 === repoCache.sha1 =>
            PullRequestUpToDate(crossDependency, update, uri)
          case Some((uri, _, _)) =>
            PullRequestOutdated(crossDependency, update, uri)
        }
    }

  private def checkUpdateStates(repo: Repo, updateStates: List[UpdateState]): F[Boolean] = {
    val outdatedStates = updateStates.collect {
      case s: DependencyOutdated  => s
      case s: PullRequestOutdated => s
    }
    val isOutdated = outdatedStates.nonEmpty
    val message = if (isOutdated) {
      val states = util.string.indentLines(outdatedStates.map(_.toString).sorted)
      s"${repo.show} is outdated:\n" + states
    } else {
      s"${repo.show} is up-to-date"
    }
    logger.info(message).as(isOutdated)
  }
}
