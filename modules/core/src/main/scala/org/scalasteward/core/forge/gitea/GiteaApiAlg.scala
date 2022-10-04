/*
 * Copyright 2018-2022 Scala Steward contributors
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

package org.scalasteward.core.forge.gitea

import cats._
import cats.implicits._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.util.HttpJsonClient
import org.http4s.{Request, Uri}
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.data._

// docs
// - https://docs.gitea.io/en-us/api-usage/
// - https://try.gitea.io/api/swagger
// - https://codeberg.org/api/swagger
object GiteaApiAlg {
  import io.circe._
  import io.circe.generic.semiauto.deriveCodec
  import org.scalasteward.core.util.uri._
  implicit val uriEncoder: Encoder[Uri] = Encoder[String].contramap[Uri](_.renderString)

  case class CreateForkOption(
      name: Option[String], // name of the forked repository
      organization: Option[String] // organization name, if forking into an organization
  )
  implicit val createForkOptionCodec: Encoder[CreateForkOption] = deriveCodec

  case class User(
      login: String,
      id: Long
  )
  implicit val userCodec: Codec[User] = deriveCodec

  case class Repository(
      fork: Boolean,
      id: Long,
      owner: User,
      name: String,
      archived: Boolean,
      clone_url: Uri,
      default_branch: String,
      parent: Option[Repository]
  )
  implicit val repositoryCodec: Codec[Repository] = deriveCodec

  case class PayloadCommit(id: Sha1)
  implicit val payloadCommitCommit: Codec[PayloadCommit] = deriveCodec

  case class BranchResp(commit: PayloadCommit)
  implicit val branchRespCodec: Codec[BranchResp] = deriveCodec

  case class PRBranchInfo(label: String, ref: String, sha: Sha1)
  implicit val prBranchInfoCodec: Codec[PRBranchInfo] = deriveCodec

  case class PullRequestResp(
      html_url: Uri,
      state: String, // open/closed/all
      number: Int,
      title: String,
      base: PRBranchInfo,
      head: PRBranchInfo
  )
  implicit val pullRequestRespCodec: Codec[PullRequestResp] = deriveCodec

  case class EditPullRequestOption(state: String)
  implicit val editPullRequestOptionCodec: Codec[EditPullRequestOption] = deriveCodec

  case class CreatePullRequestOption(
      assignee: Option[String],
      assignees: Option[Vector[String]],
      base: Option[String],
      body: Option[String],
      due_date: Option[String],
      head: Option[String],
      labels: Option[Vector[Int]],
      milestone: Option[Int],
      title: Option[String]
  )
  implicit val createPullRequestOptionCodec: Codec[CreatePullRequestOption] = deriveCodec

  case class CreateIssueCommentOption(body: String)
  implicit val createIssueCommentOptionCodec: Codec[CreateIssueCommentOption] = deriveCodec

  case class CommentResp(
      body: String,
      id: Long
  )
  implicit val commentRespCodec: Codec[CommentResp] = deriveCodec
}

final class GiteaApiAlg[F[_]: MonadThrow: HttpJsonClient](
    vcs: ForgeCfg,
    modify: Repo => Request[F] => F[Request[F]]
) extends ForgeApiAlg[F] {
  import GiteaApiAlg._

  def client: HttpJsonClient[F] = implicitly
  val url = new Url(vcs.apiHost)

  val PULL_REQUEST_PAGE_SIZE: Int = 50 // default

  def repoOut(r: Repository): RepoOut =
    RepoOut(
      name = r.name,
      owner = UserOut(r.owner.login),
      parent = r.parent.map(repoOut),
      clone_url = r.clone_url,
      default_branch = Branch(r.default_branch),
      archived = r.archived
    )

  def pullRequestOut(x: PullRequestResp): PullRequestOut = {
    val state = x.state match {
      case "open" => PullRequestState.Open
      case _      => PullRequestState.Closed
    }
    PullRequestOut(
      html_url = x.html_url,
      state = state,
      number = PullRequestNumber(x.number),
      title = x.title
    )
  }

  override def createFork(repo: Repo): F[RepoOut] =
    client
      .postWithBody[Repository, CreateForkOption](
        url.forks(repo),
        CreateForkOption(name = none, organization = none),
        modify(repo)
      )
      .map(repoOut(_))

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val create = CreatePullRequestOption(
      assignee = none,
      assignees = none,
      base = data.base.name.some,
      body = data.body.some,
      due_date = none,
      head = data.head.some,
      labels = none, // TODO
      milestone = none,
      title = data.title.some
    )
    client
      .postWithBody[PullRequestResp, CreatePullRequestOption](
        url.pulls(repo),
        create,
        modify(repo)
      )
      .map(pullRequestOut(_))
  }

  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] = {
    val edit = EditPullRequestOption(state = "closed")
    client
      .patchWithBody[PullRequestResp, EditPullRequestOption](
        url.pull(repo, number),
        edit,
        modify(repo)
      )
      .map(pullRequestOut(_))
  }

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client
      .get[BranchResp](url.repoBranch(repo, branch), modify(repo))
      .map { b =>
        BranchOut(branch, CommitOut(b.commit.id))
      }

  override def getRepo(repo: Repo): F[RepoOut] =
    client
      .get[Repository](url.repos(repo), modify(repo))
      .map(repoOut(_))

  override def listPullRequests(
      repo: Repo,
      head: String,
      base: Branch
  ): F[List[PullRequestOut]] = {
    def go(page: Int) =
      client
        .get[Vector[PullRequestResp]](
          url
            .pulls(repo)
            .withQueryParam("page", page)
            .withQueryParam("limit", PULL_REQUEST_PAGE_SIZE),
          modify(repo)
        )

    // basically unfoldEval
    def goLoop(page: Int, accu: Vector[PullRequestOut]): F[Vector[PullRequestOut]] =
      go(page).flatMap {
        case xs if xs.isEmpty => accu.pure[F]
        case xs =>
          val xs0 =
            xs.filter(x => x.head.label == head && x.base.label == base.name)
              .map(pullRequestOut)
          goLoop(page + 1, accu ++ xs0)
      }

    goLoop(1, Vector.empty).map(_.toList)
  }

  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] = {
    val create = CreateIssueCommentOption(body = comment)
    client
      .postWithBody[CommentResp, CreateIssueCommentOption](
        url.comments(repo, number),
        create,
        modify(repo)
      )
      .map { x =>
        Comment(x.body)
      }
  }

  override def labelPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      labels: List[String]
  ): F[Unit] = ().pure[F] // TODO

}
