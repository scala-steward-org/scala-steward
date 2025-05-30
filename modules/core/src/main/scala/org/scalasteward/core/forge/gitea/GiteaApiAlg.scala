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

package org.scalasteward.core.forge.gitea

import cats.*
import cats.implicits.*
import io.circe.*
import io.circe.generic.semiauto.{deriveCodec, deriveEncoder}
import org.http4s.{Request, Uri}
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.data.*
import org.scalasteward.core.forge.gitea.GiteaApiAlg.*
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.util.uri.*
import org.scalasteward.core.util.{intellijThisImportIsUsed, HttpJsonClient}
import org.typelevel.log4cats.Logger

// docs
// - https://docs.gitea.io/en-us/api-usage/
// - https://try.gitea.io/api/swagger
// - https://codeberg.org/api/swagger
object GiteaApiAlg {

  val DefaultLabelColor = "#e01060"

  case class CreateForkOption(
      name: Option[String], // name of the forked repository
      organization: Option[String] // organization name, if forking into an organization
  )
  implicit val createForkOptionEncoder: Encoder[CreateForkOption] = deriveEncoder

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

  case class Label(id: Int, name: String)
  implicit val labelCodec: Codec[Label] = deriveCodec

  case class CreateLabelReq(name: String, color: String)
  implicit val createLabelReqCodec: Codec[CreateLabelReq] = deriveCodec

  case class AttachLabelReq(labels: Vector[Int])
  implicit val attachLabelReqCodec: Codec[AttachLabelReq] = deriveCodec

  intellijThisImportIsUsed(uriEncoder)
}

final class GiteaApiAlg[F[_]: HttpJsonClient](
    vcs: ForgeCfg,
    modify: Request[F] => F[Request[F]]
)(implicit logger: Logger[F], F: MonadThrow[F])
    extends ForgeApiAlg[F] {

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
        modify
      )
      .map(repoOut(_))

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] =
    for {
      _ <- F.whenA(data.assignees.nonEmpty)(warnIfAssigneesAreUsed)
      _ <- F.whenA(data.reviewers.nonEmpty)(warnIfReviewersAreUsed)
      labels <- getOrCreateLabel(repo, data.labels.toVector)
      create = CreatePullRequestOption(
        assignee = none,
        assignees = none,
        base = data.base.name.some,
        body = data.body.some,
        due_date = none,
        head = data.head.some,
        labels = labels.some,
        milestone = none,
        title = data.title.some
      )
      resp <- client
        .postWithBody[PullRequestResp, CreatePullRequestOption](
          url.pulls(repo),
          create,
          modify
        )
    } yield pullRequestOut(resp)

  override def updatePullRequest(
      number: PullRequestNumber,
      repo: Repo,
      data: NewPullRequestData
  ): F[Unit] =
    logger.warn("Updating PRs is not yet supported for Gitea")

  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] = {
    val edit = EditPullRequestOption(state = "closed")
    client
      .patchWithBody[PullRequestResp, EditPullRequestOption](
        url.pull(repo, number),
        edit,
        modify
      )
      .map(pullRequestOut(_))
  }

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client
      .get[BranchResp](url.repoBranch(repo, branch), modify)
      .map { b =>
        BranchOut(branch, CommitOut(b.commit.id))
      }

  override def getRepo(repo: Repo): F[RepoOut] =
    client
      .get[Repository](url.repos(repo), modify)
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
          modify
        )

    // basically unfoldEval
    def goLoop(page: Int, accu: Vector[PullRequestOut]): F[Vector[PullRequestOut]] =
      go(page).flatMap {
        case xs if xs.isEmpty => accu.pure[F]
        case xs               =>
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
        modify
      )
      .map { x =>
        Comment(x.body)
      }
  }

  def getOrCreateLabel(repo: Repo, labels: Vector[String]): F[Vector[Int]] =
    listLabels(repo).flatMap { repoLabels =>
      val existing = repoLabels.filter(label => labels.contains(label.name))
      val creates =
        labels
          .filter(name => existing.exists(_.name == name))
          .traverse(createLabel(repo, _))

      creates.map(_ ++ existing).map(_.map(_.id))
    }

  def createLabel(repo: Repo, name: String): F[Label] =
    client.postWithBody[Label, CreateLabelReq](
      url.labels(repo),
      CreateLabelReq(name, DefaultLabelColor),
      modify
    )

  def listLabels(repo: Repo): F[Vector[Label]] = {
    def paging(page: Int) =
      client.get[Vector[Label]](
        url.labels(repo).withQueryParam("page", page),
        modify
      )

    def go(page: Int, accu: Vector[Label]): F[Vector[Label]] =
      paging(page).flatMap {
        case Vector() => accu.pure[F]
        case labels   => go(page + 1, accu ++ labels)
      }
    go(1, Vector.empty)
  }

  private def warnIfAssigneesAreUsed =
    logger.warn("assignees are not implemented yet for Gitea")

  private def warnIfReviewersAreUsed =
    logger.warn("reviewers are not implemented yet for Gitea")
}
