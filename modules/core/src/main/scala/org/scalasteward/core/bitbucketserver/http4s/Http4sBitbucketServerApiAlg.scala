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

package org.scalasteward.core.bitbucketserver.http4s

import cats.effect.Sync
import cats.implicits._
import org.http4s.{Request, Uri}
import org.scalasteward.core.bitbucketserver.http4s.Json.{Reviewer, User}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data.PullRequestState.Open
import org.scalasteward.core.vcs.data._

/**
  * https://docs.atlassian.com/bitbucket-server/rest/6.6.1/bitbucket-rest.html
  */
class Http4sBitbucketServerApiAlg[F[_]](
    bitbucketApiHost: Uri,
    modify: Repo => Request[F] => F[Request[F]],
    useReviewers: Boolean
)(implicit client: HttpJsonClient[F], F: Sync[F])
    extends VCSApiAlg[F] {
  val url = new StashUrls(bitbucketApiHost)

  override def createFork(repo: Repo): F[RepoOut] = ni(s"createFork($repo)")

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
    } yield PullRequestOut(pr.links("self").head.href, pr.state, pr.title)
  }

  private def useDefaultReviewers(repo: Repo): F[List[Reviewer]] =
    if (useReviewers) getDefaultReviewers(repo) else F.pure(List[Reviewer]())

  def getDefaultReviewers(repo: Repo): F[List[Reviewer]] =
    client
      .get[List[Json.Condition]](url.reviewers(repo), modify(repo))
      .map(conditions =>
        conditions
          .flatMap(condition =>
            condition.reviewers
              .map(reviewer => Reviewer(User(reviewer.name)))
          )
      )

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client
      .get[Json.Branches](url.listBranch(repo, branch), modify(repo))
      .map((a: Json.Branches) =>
        BranchOut(Branch(a.values.head.id), CommitOut(a.values.head.latestCommit))
      )

  override def getRepo(repo: Repo): F[RepoOut] =
    for {
      r <- client.get[Json.Repo](url.repo(repo), modify(repo))
      cloneUri = r.links("clone").find(_.name.contains("http")).get.href
    } yield RepoOut(r.name, UserOut(repo.owner), None, cloneUri, Branch("master"))

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Json.Page[Json.PR]](url.listPullRequests(repo, s"refs/heads/$head"), modify(repo))
      .map(_.values.map(pr => PullRequestOut(pr.links("self").head.href, pr.state, pr.title)))

  def ni(name: String): Nothing = throw new NotImplementedError(name)

}

final class StashUrls(base: Uri) {
  val api: Uri = base / "rest" / "api" / "1.0"
  val reviewerApi: Uri = base / "rest" / "default-reviewers" / "1.0"

  def repo(repo: Repo): Uri =
    api / "projects" / repo.owner / "repos" / repo.repo

  def pullRequests(r: Repo): Uri = repo(r) / "pull-requests"

  def reviewers(repo: Repo): Uri =
    reviewerApi / "projects" / repo.owner / "repos" / repo.repo / "conditions"

  def listPullRequests(r: Repo, head: String): Uri =
    pullRequests(r)
      .withQueryParam("at", head)
      .withQueryParam("limit", "1000")
      .withQueryParam("direction", "outgoing")

  def branches(r: Repo): Uri = repo(r) / "branches"

  def listBranch(r: Repo, branch: Branch): Uri =
    branches(r).withQueryParam("filterText", branch.name)
}
