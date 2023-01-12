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

package org.scalasteward.core.vcs.bitbucket

import cats.MonadThrow
import cats.syntax.all._
import org.http4s.{Request, Status}
import org.scalasteward.core.application.Config.{BitbucketCfg, VCSCfg}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.{HttpJsonClient, UnexpectedResponse}
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.bitbucket.json._
import org.scalasteward.core.vcs.data._
import org.typelevel.log4cats.Logger

/** https://developer.atlassian.com/bitbucket/api/2/reference/ */
class BitbucketApiAlg[F[_]](
    config: VCSCfg,
    bitbucketCfg: BitbucketCfg,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: MonadThrow[F]
) extends VCSApiAlg[F] {
  private val url = new Url(config.apiHost)

  override def createFork(repo: Repo): F[RepoOut] =
    for {
      fork <- client.post[RepositoryResponse](url.forks(repo), modify(repo)).recoverWith {
        case UnexpectedResponse(_, _, _, Status.BadRequest, _) =>
          client.get(url.repo(repo.copy(owner = config.login)), modify(repo))
      }
      maybeParent <-
        fork.parent
          .map(n => client.get[RepositoryResponse](url.repo(n), modify(n)))
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
    client.get[DefaultReviewers](url.defaultReviewers(repo), modify(repo)).map(_.values)

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val sourceBranchOwner = if (config.doNotFork) repo.owner else config.login
    val defaultReviewers =
      if (bitbucketCfg.useDefaultReviewers) getDefaultReviewers(repo)
      else F.pure(List.empty[Reviewer])

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
        client.postWithBody(url.pullRequests(repo), payload, modify(repo))
      }
  }

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branch(repo, branch), modify(repo))

  override def getRepo(repo: Repo): F[RepoOut] =
    for {
      repo <- client.get[RepositoryResponse](url.repo(repo), modify(repo))
      maybeParent <-
        repo.parent
          .map(n => client.get[RepositoryResponse](url.repo(n), modify(n)))
          .sequence[F, RepositoryResponse]
    } yield mapToRepoOut(repo, maybeParent)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Page[PullRequestOut]](url.listPullRequests(repo, head), modify(repo))
      .map(_.values)

  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    client.post[PullRequestOut](
      url.decline(repo, number),
      modify(repo)
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
        modify(repo)
      )
      .map((cc: CreateComment) => Comment(cc.content.raw))

  override def labelPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      labels: List[String]
  ): F[Unit] =
    logger.warn(
      "Bitbucket does not support PR labels, remove --add-labels to make this warning disappear"
    )
}
