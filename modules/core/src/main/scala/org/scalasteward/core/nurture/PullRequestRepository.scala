/*
 * Copyright 2018-2019 Scala Steward contributors
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

import cats.Applicative
import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.vcs.data.{PullRequestState, Repo}

final class PullRequestRepository[F[_]: Applicative](
    kvStore: KeyValueStore[F, Repo, Map[String, PullRequestData]]
) {
  def createOrUpdate(
      repo: Repo,
      url: Uri,
      baseSha1: Sha1,
      update: Update,
      state: PullRequestState
  ): F[Unit] =
    kvStore.update(repo) {
      case Some(prs) => prs.updated(url.toString(), PullRequestData(baseSha1, update, state))
      case None      => Map(url.toString() -> PullRequestData(baseSha1, update, state))
    }

  def findPullRequest(
      repo: Repo,
      dependency: Dependency,
      newVersion: String
  ): F[Option[(Uri, Sha1, PullRequestState)]] =
    kvStore.get(repo).map { maybePRs =>
      val pullRequests = maybePRs.fold(List.empty[(String, PullRequestData)])(_.toList)
      pullRequests
        .find {
          case (_, data) =>
            UpdateAlg
              .isUpdateFor(data.update, dependency) && data.update.nextVersion === newVersion
        }
        .map { case (url, data) => (Uri.unsafeFromString(url), data.baseSha1, data.state) }
    }
}
