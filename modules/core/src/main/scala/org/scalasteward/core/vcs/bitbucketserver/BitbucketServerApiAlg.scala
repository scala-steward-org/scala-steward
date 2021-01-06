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

package org.scalasteward.core.vcs.bitbucketserver

import cats.MonadThrow
import cats.syntax.all._
import org.http4s.{Request, Uri}
import org.scalasteward.core.application.Config.BitbucketServerCfg
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.bitbucketserver.Json.{PR, Reviewer, User}
import org.scalasteward.core.vcs.data.PullRequestState.Open
import org.scalasteward.core.vcs.data._

/** https://docs.atlassian.com/bitbucket-server/rest/latest/bitbucket-rest.html */
final class BitbucketServerApiAlg[F[_]](
    bitbucketApiHost: Uri,
    config: BitbucketServerCfg,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit client: HttpJsonClient[F], F: MonadThrow[F])
    extends VCSApiAlg[F] {
  private val url = new Url(bitbucketApiHost)

  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    getPullRequest(repo, number).flatMap { pr =>
      val out = pr.toPullRequestOut.copy(state = PullRequestState.Closed)
      declinePullRequest(repo, number, pr.version).as(out)
    }

  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] =
    client
      .postWithBody[Json.Comment, Json.Comment](
        url.comments(repo, number),
        Json.Comment(comment),
        modify(repo)
      )
      .map(comment => Comment(comment.text))

  override def createFork(repo: Repo): F[RepoOut] =
    F.raiseError(new NotImplementedError(s"createFork($repo)"))

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val fromRef =
      Json.Ref("refs/heads/" + data.head, Json.Repository(repo.repo, Json.Project(repo.owner)))
    val toRef =
      Json.Ref("refs/heads/" + data.base.name, Json.Repository(repo.repo, Json.Project(repo.owner)))

    for {
      reviewers <- useDefaultReviewers(repo)
      req = Json.NewPR(
        title = data.title,
        description = data.body,
        state = Open,
        open = true,
        closed = false,
        fromRef = fromRef,
        toRef = toRef,
        locked = false,
        reviewers = reviewers
      )
      pr <- client.postWithBody[Json.PR, Json.NewPR](url.pullRequests(repo), req, modify(repo))
    } yield pr.toPullRequestOut
  }

  private def useDefaultReviewers(repo: Repo): F[List[Reviewer]] =
    if (config.useDefaultReviewers) getDefaultReviewers(repo) else F.pure(List.empty[Reviewer])

  private def declinePullRequest(repo: Repo, number: PullRequestNumber, version: Int): F[Unit] =
    client.post_(url.declinePullRequest(repo, number, version), modify(repo))

  private def getDefaultReviewers(repo: Repo): F[List[Reviewer]] =
    client.get[List[Json.Condition]](url.reviewers(repo), modify(repo)).map { conditions =>
      conditions.flatMap { condition =>
        condition.reviewers.map(reviewer => Reviewer(User(reviewer.name)))
      }
    }

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client
      .get[Json.Branches](url.listBranch(repo, branch), modify(repo))
      .map(_.values.head.toBranchOut)

  private def getDefaultBranch(repo: Repo): F[Json.Branch] =
    client.get[Json.Branch](url.defaultBranch(repo), modify(repo))

  private def getPullRequest(repo: Repo, number: PullRequestNumber): F[PR] =
    client.get[Json.PR](url.pullRequest(repo, number), modify(repo))

  override def getRepo(repo: Repo): F[RepoOut] =
    for {
      jRepo <- client.get[Json.Repo](url.repos(repo), modify(repo))
      cloneUrl <- jRepo.cloneUrlOrRaise[F]
      defaultBranch <- getDefaultBranch(repo)
    } yield RepoOut(jRepo.slug, UserOut(repo.owner), None, cloneUrl, defaultBranch.displayId)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Json.Page[Json.PR]](url.listPullRequests(repo, s"refs/heads/$head"), modify(repo))
      .map(_.values.map(_.toPullRequestOut))
}
