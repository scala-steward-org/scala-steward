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
import org.scalasteward.core.data.*
import org.scalasteward.core.forge.data.{
  NewPullRequestData,
  PullRequestNumber,
  PullRequestOut,
  PullRequestState
}
import org.scalasteward.core.forge.{ForgeApiAlg, ForgeType}
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
  ): F[PullRequestOut] = for {
    pr <- forgeApiAlg.createPullRequest(updateData.repo, requestData)
    _ <- pullRequestRepository.createOrUpdate(updateData.repo, PullRequestData(pr, updateData))
  } yield pr

  def listPullRequestsForUpdate(data: UpdateData, forgeType: ForgeType): F[List[PullRequestOut]] = {
    val head = forgeType.pullRequestHeadFor(data.fork, data.updateBranch)
    for {
      pullRequests <- forgeApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      _ <- pullRequests.headOption.traverse_ { pr =>
        pullRequestRepository.createOrUpdate(data.repo, PullRequestData(pr, data))
      }
    } yield pullRequests
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

  private def flatTraverseOpts[T](items: F[List[T]])(f: T => F[Option[T]]): F[List[T]] =
    items.flatMap(_.flatTraverse(f(_).map(_.toList)))

  private def refreshAndEnsureStillOpen(
      repo: Repo
  ): PullRequestData[Id] => F[Option[PullRequestData[Id]]] = { stalePrData =>
    for {
      freshPr <- forgeApiAlg.getPullRequest(repo, stalePrData.number)
      _ <- pullRequestRepository.changeState(repo, stalePrData.url, freshPr.state)
      updatedPrData = stalePrData.copy(state = freshPr.state)
    } yield Option.when(updatedPrData.state == PullRequestState.Open)(updatedPrData)
  }

  def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] = for {
    pr <- forgeApiAlg.closePullRequest(repo, number)
    _ <- pullRequestRepository.changeState(repo, pr.html_url, PullRequestState.Closed)
  } yield pr
}
