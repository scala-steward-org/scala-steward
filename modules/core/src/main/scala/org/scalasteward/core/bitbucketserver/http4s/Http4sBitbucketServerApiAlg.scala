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

package org.scalasteward.core.bitbucketserver.http4s

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.{Request, Uri}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data.PullRequestState.Open
import org.scalasteward.core.vcs.data.{
  AuthenticatedUser,
  BranchOut,
  NewPullRequestData,
  PullRequestOut,
  PullRequestState,
  Repo,
  RepoOut,
  UserOut
}

/**
  * https://docs.atlassian.com/bitbucket-server/rest/6.6.1/bitbucket-rest.html
  */
class Http4sBitbucketServerApiAlg[F[_]: Sync](
    bitbucketApiHost: Uri,
    user: AuthenticatedUser,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit client: HttpJsonClient[F])
    extends VCSApiAlg[F] {
  val url = new StashUrls(bitbucketApiHost)

  override def createFork(repo: Repo): F[RepoOut] = ni(s"createFork($repo)")

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val fromRef =
      Json.Ref("refs/heads/" + data.head, Json.Repository(repo.repo, Json.Project(repo.owner)))
    val toRef =
      Json.Ref("refs/heads/" + data.base.name, Json.Repository(repo.repo, Json.Project(repo.owner)))

    val req =
      Json.NewPR(
        title = data.title,
        description = data.body,
        state = Open,
        open = true,
        closed = false,
        fromRef = fromRef,
        toRef = toRef,
        locked = false,
        reviewers = List.empty
      )

    client
      .postWithBody[Json.PR, Json.NewPR](url.pullRequests(repo), req, modify(repo))
      .map(pr => PullRequestOut(pr.links("self").head.href, pr.state, pr.title))
  }

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    ni(s"createBranch($repo,$branch)")

  override def getRepo(repo: Repo): F[RepoOut] =
    for {
      r <- client.get[Json.Repo](url.repo(repo), modify(repo))
      cloneUri = r.links("clone").find(_.name.contains("http")).get.href
    } yield RepoOut(r.name, UserOut(user.login), None, cloneUri, Branch("master"))

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Json.Page[Json.PR]](url.listPullRequests(repo, s"refs/heads/$head"), modify(repo))
      .map(_.values.map(pr => PullRequestOut(pr.links("self").head.href, pr.state, pr.title)))

  def ni(name: String): Nothing = throw new NotImplementedError(name)

  object Json {
    case class Page[A](values: List[A])

    case class Repo(name: String, forkable: Boolean, project: Project, links: Links)

    case class Project(key: String)

    type Links = Map[String, NonEmptyList[Link]]

    case class Link(href: Uri, name: Option[String])

    case class PR(title: String, state: PullRequestState, links: Links)

    case class NewPR(
        title: String,
        description: String,
        state: PullRequestState,
        open: Boolean,
        closed: Boolean,
        fromRef: Ref,
        toRef: Ref,
        locked: Boolean,
        reviewers: List[Reviewer]
    )

    case class Ref(id: String, repository: Repository)

    case class Repository(slug: String, project: Project)

    case class Reviewer(user: User)

    case class User(name: String)

    implicit def pageDecode[A: Decoder]: Decoder[Page[A]] = deriveDecoder
    implicit val repoDecode: Decoder[Repo] = deriveDecoder
    implicit val projectDecode: Decoder[Project] = deriveDecoder
    implicit val linkDecoder: Decoder[Link] = deriveDecoder
    implicit val uriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri.unsafeFromString)
    implicit val prDecoder: Decoder[PR] = deriveDecoder

    implicit val encodeNewPR: Encoder[NewPR] = deriveEncoder
    implicit val encodeRef: Encoder[Ref] = deriveEncoder
    implicit val encodeRepository: Encoder[Repository] = deriveEncoder
    implicit val encodeProject: Encoder[Project] = deriveEncoder
    implicit val encodeReviewer: Encoder[Reviewer] = deriveEncoder
    implicit val encodeUser: Encoder[User] = deriveEncoder
  }
}

final class StashUrls(base: Uri) {
  val api: Uri = base / "rest" / "api" / "1.0"

  def repo(repo: Repo): Uri =
    api / "projects" / repo.owner / "repos" / repo.repo

  def pullRequests(r: Repo): Uri = repo(r) / "pull-requests"

  def listPullRequests(r: Repo, head: String): Uri =
    pullRequests(r)
      .withQueryParam("at", head)
      .withQueryParam("limit", "1000")
      .withQueryParam("direction", "outgoing")
}
