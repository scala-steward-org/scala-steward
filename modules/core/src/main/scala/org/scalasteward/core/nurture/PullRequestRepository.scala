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

import cats.implicits.*
import cats.{Id, Monad}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Uri
import org.scalasteward.core.data.*
import org.scalasteward.core.forge.data.{PullRequestNumber, PullRequestState}
import org.scalasteward.core.git
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.nurture.PullRequestRepository.Entry
import org.scalasteward.core.persistence.KeyValueStore
import org.scalasteward.core.repoconfig.RetractedArtifact
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.{DateTimeAlg, Timestamp}

final class PullRequestRepository[F[_]](kvStore: KeyValueStore[F, Repo, Map[Uri, Entry]])(implicit
    dateTimeAlg: DateTimeAlg[F],
    F: Monad[F]
) {

  /** Changes the state of a pull request. A `closedReason` is only kept while the pull request is
    * closed; it is dropped as soon as the pull request is open again.
    */
  def changeState(
      repo: Repo,
      url: Uri,
      newState: PullRequestState,
      closedReason: Option[ClosedReason] = None
  ): F[Unit] =
    kvStore
      .modifyF(repo) { maybePullRequests =>
        val pullRequests = maybePullRequests.getOrElse(Map.empty)
        pullRequests.get(url).traverse { found =>
          val newReason = if (newState.isClosed) closedReason.orElse(found.closedReason) else None
          val entry = found.copy(state = newState, closedReason = newReason)
          pullRequests.updated(url, entry).pure[F]
        }
      }
      .void

  def createOrUpdate(repo: Repo, data: PullRequestData[Id]): F[Unit] =
    kvStore
      .modifyF(repo) { maybePullRequests =>
        val pullRequests = maybePullRequests.getOrElse(Map.empty)
        val found = pullRequests.get(data.url)
        found
          .fold(dateTimeAlg.currentTimestamp)(_.entryCreatedAt.pure[F])
          .map { createdAt =>
            val entry = Entry(
              data.baseSha1,
              data.update,
              data.state,
              createdAt,
              Some(data.number),
              Some(data.updateBranch),
              found.flatMap(_.closedReason).filter(_ => data.state.isClosed),
              found.flatMap(_.absentSince)
            )
            pullRequests.updated(data.url, entry).some
          }
      }
      .void

  def getObsoleteOpenPullRequests(repo: Repo, update: Update.Single): F[List[PullRequestData[Id]]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.collect {
        case (url, Entry(baseSha1, u: Update.Single, state, _, number, updateBranch, _, _))
            if state === PullRequestState.Open && update.supersedes(u) =>
          for {
            number <- number
            branch = updateBranch.getOrElse(git.branchFor(u, repo.branch))
          } yield PullRequestData[Id](url, baseSha1, u, state, number, branch)
      }.flatten.toList.sortBy(_.number.value)
    }

  def getRetractedOpenPullRequests(
      repo: Repo,
      allRetractedArtifacts: List[RetractedArtifact]
  ): F[List[(PullRequestData[Id], RetractedArtifact)]] =
    kvStore.getOrElse(repo, Map.empty).map { pullRequets =>
      pullRequets.flatMap {
        case (
              url,
              Entry(
                baseSha1,
                u: Update.Single,
                PullRequestState.Open,
                _,
                number,
                updateBranch,
                _,
                _
              )
            ) =>
          for {
            prNumber <- number
            retractedArtifact <- allRetractedArtifacts.find(_.isRetracted(u))
          } yield {
            val branch = updateBranch.getOrElse(git.branchFor(u, repo.branch))
            val data =
              PullRequestData[Id](url, baseSha1, u, PullRequestState.Open, prNumber, branch)
            (data, retractedArtifact)
          }
        case _ => Map.empty
      }.toList
    }

  /** Finds the open pull requests that are no longer needed given `dependencies`, the ones of the
    * base branch at `baseSha1`, and `pendingUpdates`, the updates for them. Each is paired with its
    * reason.
    *
    * A removed dependency only counts once it has been found absent at two different `baseSha1`s,
    * to be robust against a single bad scan of the build. For that, this also records the first
    * sighting of an absence as `absentSince` and clears it as soon as the dependency is back.
    */
  def findNoLongerNeededOpenPullRequests(
      repo: Repo,
      dependencies: List[Dependency],
      pendingUpdates: List[Update.ForArtifactId],
      baseSha1: Sha1
  ): F[List[(PullRequestData[Id], NoLongerNeeded)]] =
    kvStore.get(repo).flatMap { maybePullRequests =>
      val pullRequests = maybePullRequests.getOrElse(Map.empty)
      val checked = pullRequests.toList.map { case (url, entry) =>
        val verdict = entry.update match {
          case u: Update.Single if !entry.state.isClosed =>
            u.noLongerNeededIn(dependencies, pendingUpdates)
          case _ => None
        }
        val absentSince =
          if (verdict.contains(NoLongerNeeded.DependencyRemoved))
            entry.absentSince.orElse(Some(baseSha1))
          else None
        val reason = verdict.filter {
          case NoLongerNeeded.DependencyRemoved => absentSince.exists(_ =!= baseSha1)
          case NoLongerNeeded.AlreadyApplied(_) => true
        }
        (url, entry.copy(absentSince = absentSince), reason)
      }
      val updated = checked.map { case (url, entry, _) => url -> entry }.toMap
      val noLongerNeeded = checked.flatMap {
        case (url, entry, Some(reason)) =>
          (entry.update, entry.number) match {
            case (u: Update.Single, Some(number)) =>
              val branch = entry.updateBranch.getOrElse(git.branchFor(u, repo.branch))
              Some(
                (PullRequestData[Id](url, entry.baseSha1, u, entry.state, number, branch), reason)
              )
            case _ => None
          }
        case _ => None
      }
      F.whenA(updated != pullRequests)(kvStore.set(repo, Some(updated)))
        .as(noLongerNeeded.sortBy(_._1.number.value))
    }

  /** Whether Scala Steward closed the pull request itself because the base branch made its update
    * unnecessary, see [[ClosedReason]].
    */
  def wasClosedAsNoLongerNeeded(repo: Repo, url: Uri): F[Boolean] =
    kvStore.get(repo).map {
      _.flatMap(_.get(url)).exists(entry => entry.state.isClosed && entry.closedReason.nonEmpty)
    }

  def findLatestPullRequest(
      repo: Repo,
      crossDependency: CrossDependency,
      newVersion: Version
  ): F[Option[PullRequestData[Option]]] =
    kvStore.getOrElse(repo, Map.empty).map {
      _.filter {
        case (_, Entry(_, u: Update.Single, _, _, _, _, _, _)) =>
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
      case None               => Map.empty
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
      updateBranch: Option[Branch],
      closedReason: Option[ClosedReason] = None,
      absentSince: Option[Sha1] = None
  )

  object Entry {
    implicit val entryCodec: Codec[Entry] =
      deriveCodec
  }
}
