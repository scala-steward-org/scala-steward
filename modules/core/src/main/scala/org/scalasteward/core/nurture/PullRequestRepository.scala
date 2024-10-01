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

import cats.implicits._
import cats.{Id, Monad}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Uri
import org.scalasteward.core.data._
import org.scalasteward.core.forge.data.{PullRequestNumber, PullRequestState}
import org.scalasteward.core.git
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.nurture.PullRequestRepository.Entry
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}

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

  def getObsoleteOpenPullRequests(repo: Repo, update: Update.Single): F[List[PullRequestData[Id]]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.collect {
        case (url, Entry(baseSha1, u: Update.Single, state, _, number, updateBranch))
            if state === PullRequestState.Open &&
              u.withNewerVersions(update.newerVersions) === update &&
              u.nextVersion < update.nextVersion =>
          for {
            number <- number
            branch = updateBranch.getOrElse(git.branchFor(u, repo.branch))
          } yield PullRequestData[Id](url, baseSha1, u, state, number, branch)
      }.flatten.toList.sortBy(_.number.value)
    }

  def findLatestPullRequest(
      repo: Repo,
      crossDependency: CrossDependency,
      newVersion: Version
  ): F[Option[PullRequestData[Option]]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.filter {
        case (_, Entry(_, u: Update.Single, _, _, _, _)) =>
          UpdateAlg.isUpdateFor(u, crossDependency) && u.nextVersion === newVersion
        case _ => false
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

  def lastPullRequestCreatedAtByArtifact(repo: Repo): F[Map[(GroupId, String), Timestamp]] =
    kvStore.get(repo).map {
      case None => Map.empty
      case Some(pullRequests) =>
        pullRequests.values
          .flatMap { entry =>
            entry.update.asSingleUpdates.map(_.groupAndMainArtifactId -> entry.entryCreatedAt)
          }
          .groupMapReduce(_._1)(_._2)(_ max _)
    }
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
