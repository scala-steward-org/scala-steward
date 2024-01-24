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

package org.scalasteward.core.forge.bitbucket

import cats.MonadThrow
import cats.syntax.all._
import org.http4s.{Request, Status}
import org.scalasteward.core.application.Config.{BitbucketCfg, ForgeCfg}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.bitbucket.json._
import org.scalasteward.core.forge.data._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.{HttpJsonClient, UnexpectedResponse}
import org.typelevel.log4cats.Logger

/** https://developer.atlassian.com/bitbucket/api/2/reference/ */
class BitbucketApiAlg[F[_]](
    config: ForgeCfg,
    bitbucketCfg: BitbucketCfg,
    modify: Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: MonadThrow[F]
) extends ForgeApiAlg[F] {
  private val url = new Url(config.apiHost)

  override def createFork(repo: Repo): F[RepoOut] =
    for {
      fork <- client.post[RepositoryResponse](url.forks(repo), modify).recoverWith {
        case UnexpectedResponse(_, _, _, Status.BadRequest, _) =>
          client.get(url.repo(repo.copy(owner = config.login)), modify)
      }
      maybeParent <-
        fork.parent
          .map(n => client.get[RepositoryResponse](url.repo(n), modify))
          .sequence[F, RepositoryResponse]
    } yield mapToRepoOut(fork, maybeParent)

  private def mapToRepoOut(
      repo: RepositoryResponse,
      maybeParent: Option[RepositoryResponse]
  ): RepoOut =
    RepoOut(
      repo.name,
      repo.owner,
      maybeParent.map(p => mapToRepoOut(p, None)),
      repo.httpsCloneUrl,
      repo.mainBranch
    )

  private def getDefaultReviewers(repo: Repo): F[List[Reviewer]] =
    client.get[DefaultReviewers](url.defaultReviewers(repo), modify).map(_.values)

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val sourceBranchOwner = if (config.doNotFork) repo.owner else config.login
    val defaultReviewers =
      if (bitbucketCfg.useDefaultReviewers) getDefaultReviewers(repo)
      else F.pure(List.empty[Reviewer])

    val create: F[PullRequestOut] =
      defaultReviewers
        .map(reviewers =>
          CreatePullRequestRequest(
            data.title,
            Branch(data.head),
            Repo(sourceBranchOwner, repo.repo, repo.branch),
            data.base,
            data.body,
            reviewers
          )
        )
        .flatMap { payload =>
          client.postWithBody(url.pullRequests(repo), payload, modify)
        }

    for {
      _ <- F.whenA(data.assignees.nonEmpty)(warnIfAssigneesAreUsed)
      _ <- F.whenA(data.reviewers.nonEmpty)(warnIfReviewersAreUsed)
      _ <- F.whenA(data.labels.nonEmpty)(warnIfLabelsAreUsed)
      pullRequestOut <- create
    } yield pullRequestOut
  }

  override def updatePullRequest(
      number: PullRequestNumber,
      repo: Repo,
      data: NewPullRequestData
  ): F[Unit] =
    logger.warn("Updating PRs is not yet supported for Bitbucket")

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branch(repo, branch), modify)

  override def getRepo(repo: Repo): F[RepoOut] =
    for {
      repo <- client.get[RepositoryResponse](url.repo(repo), modify)
      maybeParent <-
        repo.parent
          .map(n => client.get[RepositoryResponse](url.repo(n), modify))
          .sequence[F, RepositoryResponse]
    } yield mapToRepoOut(repo, maybeParent)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Page[PullRequestOut]](url.listPullRequests(repo, head), modify)
      .map(_.values)

  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    client.post[PullRequestOut](
      url.decline(repo, number),
      modify
    )

  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] =
    client
      .postWithBody[CreateComment, CreateComment](
        url.comments(repo, number),
        CreateComment(comment),
        modify
      )
      .map((cc: CreateComment) => Comment(cc.content.raw))

  private def warnIfLabelsAreUsed =
    logger.warn(
      "Bitbucket does not support PR labels, remove --add-labels to make this warning disappear"
    )

  private def warnIfAssigneesAreUsed =
    logger.warn("assignees are not supported by Bitbucket")

  private def warnIfReviewersAreUsed =
    logger.warn("reviewers are not implemented yet for Bitbucket")
}
