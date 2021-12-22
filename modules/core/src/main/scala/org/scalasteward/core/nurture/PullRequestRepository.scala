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

import cats.implicits._
import cats.{Id, Monad}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Uri
import org.scalasteward.core.data.{CrossDependency, Update, Version}
import org.scalasteward.core.git
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.nurture.PullRequestRepository.Entry
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}
import org.scalasteward.core.vcs.data.{PullRequestNumber, PullRequestState, Repo}

final class PullRequestRepository[F[_]](kvStore: KeyValueStore[F, Repo, Map[Uri, Entry]])(implicit
    dateTimeAlg: DateTimeAlg[F],
    F: Monad[F]
) {
  def changeState(repo: Repo, url: Uri, newState: PullRequestState): F[Unit] =
    kvStore
      .modifyF(repo) { maybePullRequests =>
        val pullRequests = maybePullRequests.getOrElse(Map.empty)
        pullRequests.get(url).traverse { found =>
          val entry = found.copy(state = newState)
          pullRequests.updated(url, entry).pure[F]
        }
      }
      .void

  def createOrUpdate(repo: Repo, data: PullRequestData[Id]): F[Unit] =
    kvStore
      .modifyF(repo) { maybePullRequests =>
        val pullRequests = maybePullRequests.getOrElse(Map.empty)
        pullRequests
          .get(data.url)
          .fold(dateTimeAlg.currentTimestamp)(_.entryCreatedAt.pure[F])
          .map { createdAt =>
            val entry = Entry(
              data.baseSha1,
              data.update,
              data.state,
              createdAt,
              Some(data.number),
              Some(data.updateBranch)
            )
            pullRequests.updated(data.url, entry).some
          }
      }
      .void

  def getObsoleteOpenPullRequests(repo: Repo, update: Update): F[List[PullRequestData[Id]]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.collect {
        case (url, entry)
            if entry.state === PullRequestState.Open &&
              entry.update.withNewerVersions(update.newerVersions) === update &&
              Version(entry.update.nextVersion) < Version(update.nextVersion) =>
          for {
            number <- entry.number
            updateBranch = entry.updateBranch.getOrElse(git.branchFor(entry.update, repo.branch))
          } yield PullRequestData[Id](
            url,
            entry.baseSha1,
            entry.update,
            entry.state,
            number,
            updateBranch
          )
      }.flatten.toList.sortBy(_.number.value)
    }

  def findLatestPullRequest(
      repo: Repo,
      crossDependency: CrossDependency,
      newVersion: String
  ): F[Option[PullRequestData[Option]]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.filter { case (_, entry) =>
        UpdateAlg.isUpdateFor(entry.update, crossDependency) &&
          entry.update.nextVersion === newVersion
      }
        .maxByOption { case (_, entry) => entry.entryCreatedAt.millis }
        .map { case (url, entry) =>
          PullRequestData(
            url,
            entry.baseSha1,
            entry.update,
            entry.state,
            entry.number,
            entry.updateBranch
          )
        }
    }

  def lastPullRequestCreatedAt(repo: Repo): F[Option[Timestamp]] =
    kvStore.get(repo).map(_.flatMap(_.values.map(_.entryCreatedAt).maxOption))
}

object PullRequestRepository {
  final case class Entry(
      baseSha1: Sha1,
      update: Update,
      state: PullRequestState,
      entryCreatedAt: Timestamp,
      number: Option[PullRequestNumber],
      updateBranch: Option[Branch]
  )

  object Entry {
    implicit val entryCodec: Codec[Entry] =
      deriveCodec
  }
}
