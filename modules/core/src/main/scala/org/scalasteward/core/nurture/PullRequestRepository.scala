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

package org.scalasteward.core.nurture

import cats.Monad
import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.data.{CrossDependency, Update}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}
import org.scalasteward.core.vcs.data.{PullRequestState, Repo}

final class PullRequestRepository[F[_]](
    kvStore: KeyValueStore[F, Repo, Map[Uri, PullRequestData]]
)(implicit
    dateTimeAlg: DateTimeAlg[F],
    F: Monad[F]
) {
  def createOrUpdate(
      repo: Repo,
      url: Uri,
      baseSha1: Sha1,
      update: Update,
      state: PullRequestState
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
              val data = PullRequestData(baseSha1, update, state, now)
              pullRequests.updated(url, data).some
            }
        }
      }
      .void

  def findPullRequest(
      repo: Repo,
      crossDependency: CrossDependency,
      newVersion: String
  ): F[Option[(Uri, Sha1, PullRequestState)]] =
    kvStore.get(repo).map {
      _.getOrElse(Map.empty).collectFirst {
        case (url, data)
            if UpdateAlg.isUpdateFor(data.update, crossDependency) &&
              data.update.nextVersion === newVersion =>
          (url, data.baseSha1, data.state)
      }
    }

  def lastPullRequestCreatedAt(repo: Repo): F[Option[Timestamp]] =
    kvStore.get(repo).map(_.flatMap(_.values.map(_.entryCreatedAt).maxOption))
}
