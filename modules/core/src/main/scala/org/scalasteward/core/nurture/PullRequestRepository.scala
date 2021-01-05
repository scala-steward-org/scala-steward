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

import cats.Monad
import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.data.{CrossDependency, Update, Version}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}
import org.scalasteward.core.vcs
import org.scalasteward.core.vcs.data.{PullRequestNumber, PullRequestState, Repo}

final class PullRequestRepository[F[_]](
    kvStore: KeyValueStore[F, Repo, Map[Uri, PullRequestData]]
)(implicit
    dateTimeAlg: DateTimeAlg[F],
    F: Monad[F]
) {
  def changeState(repo: Repo, url: Uri, newState: PullRequestState): F[Unit] =
    kvStore
      .modifyF(repo) { maybePullRequests =>
        val pullRequests = maybePullRequests.getOrElse(Map.empty)
        pullRequests.get(url).traverse { found =>
          val data = found.copy(state = newState)
          pullRequests.updated(url, data).pure[F]
        }
      }
      .void

  def createOrUpdate(
      repo: Repo,
      url: Uri,
      baseSha1: Sha1,
      update: Update,
      state: PullRequestState,
      number: PullRequestNumber
  ): F[Unit] =
    kvStore
      .modifyF(repo) { maybePullRequests =>
        val pullRequests = maybePullRequests.getOrElse(Map.empty)
        pullRequests.get(url) match {
          case Some(found) =>
            val data = found.copy(baseSha1, update, state)
            pullRequests.updated(url, data).some.pure[F]
          case None =>
            dateTimeAlg.currentTimestamp.map { now =>
              val data = PullRequestData(baseSha1, update, state, now, Some(number))
              pullRequests.updated(url, data).some
            }
        }
      }
      .void

  def getObsoleteOpenPullRequests(
      repo: Repo,
      update: Update
  ): F[List[(PullRequestNumber, Uri, Update)]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.collect {
        case (url, data)
            if data.update.withNewerVersions(update.newerVersions) === update &&
              Version(data.update.nextVersion) < Version(update.nextVersion) &&
              data.state === PullRequestState.Open =>
          data.number.orElse(vcs.extractPullRequestNumberFrom(url)).map((_, url, data.update))
      }.flatten.toList.sortBy { case (number, _, _) => number.value }
    }

  def findLatestPullRequest(
      repo: Repo,
      crossDependency: CrossDependency,
      newVersion: String
  ): F[Option[(Uri, Sha1, PullRequestState)]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.filter { case (_, data) =>
        UpdateAlg.isUpdateFor(data.update, crossDependency) &&
          data.update.nextVersion === newVersion
      }
        .maxByOption { case (_, data) => data.entryCreatedAt.millis }
        .map { case (url, data) => (url, data.baseSha1, data.state) }
    }

  def lastPullRequestCreatedAt(repo: Repo): F[Option[Timestamp]] =
    kvStore.get(repo).map(_.flatMap(_.values.map(_.entryCreatedAt).maxOption))
}
