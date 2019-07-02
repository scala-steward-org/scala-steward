/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.gitlab.http4s

import cats.Monad
import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import org.scalasteward.core.util.HttpJsonClient
import org.http4s.client.UnexpectedStatus
import org.http4s.{Request, Status, Uri}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.gitlab._
import org.scalasteward.core.util.uri.uriDecoder
import org.scalasteward.core.util.MonadThrowable

final private[http4s] case class ForkPayload(id: String, namespace: String)
final private[http4s] case class MergeRequestPayload(
    id: String,
    title: String,
    description: String,
    target_project_id: Long,
    source_branch: String,
    target_branch: Branch
)
private[http4s] object MergeRequestPayload {
  def apply(id: String, projectId: Long, data: NewPullRequestData): MergeRequestPayload =
    MergeRequestPayload(id, data.title, data.body, projectId, data.head, data.base)
}
final private[http4s] case class MergeRequestOut(
    web_url: Uri,
    state: PullRequestState,
    title: String
) {
  val pullRequestOut: PullRequestOut = PullRequestOut(web_url, state, title)
}
final private[http4s] case class CommitId(id: Sha1) {
  val commitOut: CommitOut = CommitOut(id)
}
final private[http4s] case class ProjectId(id: Long)

private[http4s] object GitlabJsonCodec {
  // prevent IntelliJ from removing the import of uriDecoder
  locally(uriDecoder)

  implicit val forkPayloadEncoder: Encoder[ForkPayload] = deriveEncoder
  implicit val userOutDecoder: Decoder[UserOut] = Decoder.instance {
    _.downField("username").as[String].map(UserOut(_))
  }
  val parentRepoOutDecoder: Decoder[RepoOut] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      ownerC = c.downField("namespace")
      owner <- ownerC.downField("name").as[String]
      _ = c.up
      cloneUrl <- c.downField("http_url_to_repo").as[Uri]
      defaultBranch <- c
        .downField("default_branch")
        .as[Option[Branch]]
        .map(_.getOrElse(Branch("master")))
    } yield RepoOut(name, UserOut(owner), None, cloneUrl, defaultBranch)
  }
  implicit val repoOutDecoder: Decoder[RepoOut] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      owner <- c.downField("owner").as[UserOut]
      cloneUrl <- c.downField("http_url_to_repo").as[Uri]
      parent <- c
        .downField("forked_from_project")
        .as[Option[RepoOut]](Decoder.decodeOption(parentRepoOutDecoder))
      defaultBranch <- c
        .downField("default_branch")
        .as[Option[Branch]]
        .map(_.getOrElse(Branch("master")))
    } yield RepoOut(name, owner, parent, cloneUrl, defaultBranch)
  }

  implicit val projectIdDecoder: Decoder[ProjectId] = deriveDecoder
  implicit val mergeRequestPayloadEncoder: Encoder[MergeRequestPayload] = deriveEncoder
  implicit val PullRequestOutDecoder: Decoder[PullRequestOut] =
    deriveDecoder[MergeRequestOut].map(_.pullRequestOut)
  implicit val commitOutDecoder: Decoder[CommitOut] = deriveDecoder[CommitId].map(_.commitOut)
  implicit val branchOutDecoder: Decoder[BranchOut] = deriveDecoder[BranchOut]
}

class Http4sGitLabApiAlg[F[_]: MonadThrowable](
    gitlabApiHost: Uri,
    user: AuthenticatedUser,
    modify: Repo => Request[F] => F[Request[F]]
)(
    implicit
    client: HttpJsonClient[F]
) extends VCSApiAlg[F] {
  import GitlabJsonCodec._

  val url = new Url(gitlabApiHost)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client.get(url.listMergeRequests(repo, head), modify(repo))

  def createFork(repo: Repo): F[RepoOut] = {
    val userOwnedRepo = repo.copy(owner = user.login)
    val data = ForkPayload(url.encodedProjectId(userOwnedRepo), user.login)
    client
      .postWithBody[RepoOut, ForkPayload](url.createFork(repo), data, modify(repo))
      .handleErrorWith {
        case UnexpectedStatus(Status.Conflict) => getRepo(userOwnedRepo)
      }
  }

  def createPullRequest(repo: Repo, data: NewPullRequestData)(
      implicit F: Monad[F]
  ): F[PullRequestOut] = {
    val userOwnedRepo = repo.copy(owner = user.login)
    for {
      projectId <- client.get[ProjectId](url.repos(repo), modify(repo))
      payload = MergeRequestPayload(url.encodedProjectId(userOwnedRepo), projectId.id, data)
      res <- client.postWithBody[PullRequestOut, MergeRequestPayload](
        url.mergeRequest(userOwnedRepo),
        payload,
        modify(repo)
      )
    } yield res
  }

  def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.getBranch(repo, branch), modify(repo))

  def getRepo(repo: Repo): F[RepoOut] =
    client.get(url.repos(repo), modify(repo))
}
