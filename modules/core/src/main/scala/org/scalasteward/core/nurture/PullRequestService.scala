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

import cats.*
import cats.syntax.all.*
import org.http4s.Uri
import org.scalasteward.core.data.*
import org.scalasteward.core.forge.data.{
  NewPullRequestData,
  PullRequestNumber,
  PullRequestOut,
  PullRequestState
}
import org.scalasteward.core.forge.{ForgeApiAlg, ForgeType}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.repoconfig.RetractedArtifact

final class PullRequestService[F[_]](
    pullRequestRepository: PullRequestRepository[F],
    forgeApiAlg: ForgeApiAlg[F]
)(implicit
    F: Monad[F]
) {

  def createPullRequest(
      updateData: UpdateData,
      requestData: NewPullRequestData
  ): F[PullRequestOut] = forgeApiAlg
    .createPullRequest(updateData.repo, requestData)
    .flatTap(pr =>
      pullRequestRepository.createOrUpdate(updateData.repo, PullRequestData(pr, updateData))
    )

  def closePullRequest(
      repo: Repo,
      number: PullRequestNumber,
      closedReason: Option[ClosedReason] = None
  ): F[PullRequestOut] = forgeApiAlg
    .closePullRequest(repo, number)
    .flatTap { pr =>
      pullRequestRepository.changeState(repo, pr.html_url, PullRequestState.Closed, closedReason)
    }

  def wasClosedAsNoLongerNeeded(repo: Repo, url: Uri): F[Boolean] =
    pullRequestRepository.wasClosedAsNoLongerNeeded(repo, url)

  /** Finds the pull request for the update branch of `data` and refreshes its entry in the local
    * repository. An open PR is preferred; otherwise the newest closed one counts, independent of
    * the order in which the forge lists them.
    */
  def findPullRequestForUpdate(
      data: UpdateData,
      forgeType: ForgeType
  ): F[Option[PullRequestOut]] = {
    val head = forgeType.pullRequestHeadFor(data.fork, data.updateBranch)
    forgeApiAlg.listPullRequests(data.repo, head, data.baseBranch).flatMap { pullRequests =>
      val maybePr = pullRequests
        .find(!_.state.isClosed)
        .orElse(pullRequests.maxByOption(_.number.value))
      maybePr
        .traverse_ { pr =>
          pullRequestRepository.createOrUpdate(data.repo, PullRequestData(pr, data))
        }
        .as(maybePr)
    }
  }

  def getObsoleteOpenPullRequests(repo: Repo, update: Update.Single): F[List[PullRequestData[Id]]] =
    flatTraverseOpts(pullRequestRepository.getObsoleteOpenPullRequests(repo, update))(
      refreshAndEnsureStillOpen(repo)
    )

  def getRetractedOpenPullRequests(
      repo: Repo,
      allRetractedArtifacts: List[RetractedArtifact]
  ): F[List[(PullRequestData[Id], RetractedArtifact)]] =
    flatTraverseOpts(
      pullRequestRepository.getRetractedOpenPullRequests(repo, allRetractedArtifacts)
    ) { case (stalePr, retractedArtifact) =>
      refreshAndEnsureStillOpen(repo)(stalePr).map(_.map(_ -> retractedArtifact))
    }

  def findNoLongerNeededOpenPullRequests(
      repo: Repo,
      dependencies: List[Dependency],
      pendingUpdates: List[Update.ForArtifactId],
      baseSha1: Sha1
  ): F[List[(PullRequestData[Id], NoLongerNeeded)]] =
    flatTraverseOpts(
      pullRequestRepository
        .findNoLongerNeededOpenPullRequests(repo, dependencies, pendingUpdates, baseSha1)
    ) { case (stalePr, reason) =>
      refreshAndEnsureStillOpen(repo)(stalePr).map(_.map(_ -> reason))
    }

  private def flatTraverseOpts[T](items: F[List[T]])(f: T => F[Option[T]]): F[List[T]] =
    items.flatMap(_.traverseFilter(f))

  private def refreshAndEnsureStillOpen(
      repo: Repo
  ): PullRequestData[Id] => F[Option[PullRequestData[Id]]] = { stalePrData =>
    forgeApiAlg
      .getPullRequest(repo, stalePrData.number)
      .flatTap(freshPr => pullRequestRepository.changeState(repo, stalePrData.url, freshPr.state))
      .map(freshPr =>
        Option.when(freshPr.state == PullRequestState.Open)(stalePrData.copy(state = freshPr.state))
      )
  }
}
