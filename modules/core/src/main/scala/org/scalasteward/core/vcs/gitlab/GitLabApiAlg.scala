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

package org.scalasteward.core.vcs.gitlab

import cats.MonadThrow
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s.{Request, Status, Uri}
import org.scalasteward.core.application.Config.GitLabCfg
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.util.uri.uriDecoder
import org.scalasteward.core.util.{HttpJsonClient, UnexpectedResponse}
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data._

final private[gitlab] case class ForkPayload(id: String, namespace: String)
final private[gitlab] case class MergeRequestPayload(
    id: String,
    title: String,
    description: String,
    target_project_id: Long,
    source_branch: String,
    target_branch: Branch
)

private[gitlab] object MergeRequestPayload {
  def apply(id: String, projectId: Long, data: NewPullRequestData): MergeRequestPayload =
    MergeRequestPayload(id, data.title, data.body, projectId, data.head, data.base)
}

final private[gitlab] case class MergeRequestOut(
    webUrl: Uri,
    state: PullRequestState,
    title: String,
    iid: PullRequestNumber,
    mergeStatus: String
) {
  val pullRequestOut: PullRequestOut = PullRequestOut(webUrl, state, iid, title)
}

final private[gitlab] case class CommitId(id: Sha1) {
  val commitOut: CommitOut = CommitOut(id)
}
final private[gitlab] case class ProjectId(id: Long)

private[gitlab] object GitLabMergeStatus {
  val CanBeMerged = "can_be_merged"
  val CannotBeMerged = "cannot_be_merged"
  val Checking = "checking"
}

private[gitlab] object GitLabJsonCodec {
  // prevent IntelliJ from removing the import of uriDecoder
  locally(uriDecoder)

  implicit val forkPayloadEncoder: Encoder[ForkPayload] = deriveEncoder
  implicit val userOutDecoder: Decoder[UserOut] = Decoder.instance {
    _.downField("username").as[String].map(UserOut(_))
  }
  implicit val repoOutDecoder: Decoder[RepoOut] = Decoder.instance { c =>
    for {
      name <- c.downField("path").as[String]
      owner <-
        c.downField("owner")
          .as[UserOut]
          .orElse(c.downField("namespace").downField("full_path").as[String].map(UserOut(_)))
      cloneUrl <- c.downField("http_url_to_repo").as[Uri]
      parent <-
        c.downField("forked_from_project")
          .as[Option[RepoOut]]
      defaultBranch <-
        c.downField("default_branch")
          .as[Option[Branch]]
          .map(_.getOrElse(Branch("master")))
    } yield RepoOut(name, owner, parent, cloneUrl, defaultBranch)
  }

  implicit val mergeRequestOutDecoder: Decoder[MergeRequestOut] = Decoder.instance { c =>
    for {
      webUrl <- c.downField("web_url").as[Uri]
      state <- c.downField("state").as[PullRequestState]
      title <- c.downField("title").as[String]
      iid <- c.downField("iid").as[PullRequestNumber]
      mergeStatus <- c.downField("merge_status").as[String]
    } yield MergeRequestOut(webUrl, state, title, iid, mergeStatus)
  }

  implicit val projectIdDecoder: Decoder[ProjectId] = deriveDecoder
  implicit val mergeRequestPayloadEncoder: Encoder[MergeRequestPayload] = deriveEncoder
  implicit val updateStateEncoder: Encoder[UpdateState] = Encoder.instance { newState =>
    val encoded = newState.state match {
      case PullRequestState.Open   => "open"
      case PullRequestState.Closed => "close"
    }
    Json.obj("state_event" -> encoded.asJson)
  }

  implicit val pullRequestOutDecoder: Decoder[PullRequestOut] =
    mergeRequestOutDecoder.map(_.pullRequestOut)
  implicit val commitOutDecoder: Decoder[CommitOut] = deriveDecoder[CommitId].map(_.commitOut)
  implicit val branchOutDecoder: Decoder[BranchOut] = deriveDecoder[BranchOut]
}

final class GitLabApiAlg[F[_]](
    gitlabApiHost: Uri,
    doNotFork: Boolean,
    config: GitLabCfg,
    user: AuthenticatedUser,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: MonadThrow[F]
) extends VCSApiAlg[F] {
  import GitLabJsonCodec._

  private val url = new Url(gitlabApiHost)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client.get(url.listMergeRequests(repo, head, base.name), modify(repo))

  override def createFork(repo: Repo): F[RepoOut] = {
    val userOwnedRepo = repo.copy(owner = user.login)
    val data = ForkPayload(url.encodedProjectId(userOwnedRepo), user.login)
    client
      .postWithBody[RepoOut, ForkPayload](url.createFork(repo), data, modify(repo))
      .recoverWith {
        case UnexpectedResponse(_, _, _, Status.Conflict, _) => getRepo(userOwnedRepo)
        // workaround for https://gitlab.com/gitlab-org/gitlab-ce/issues/65275
        // see also https://github.com/scala-steward-org/scala-steward/pull/761
        case UnexpectedResponse(_, _, _, Status.NotFound, _) => getRepo(userOwnedRepo)
      }
  }

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val targetRepo = if (doNotFork) repo else repo.copy(owner = user.login)
    val mergeRequest = for {
      projectId <- client.get[ProjectId](url.repos(repo), modify(repo))
      payload = MergeRequestPayload(url.encodedProjectId(targetRepo), projectId.id, data)
      res <- client.postWithBody[MergeRequestOut, MergeRequestPayload](
        url.mergeRequest(targetRepo),
        payload,
        modify(repo)
      )
    } yield res

    def waitForMergeRequestStatus(
        number: PullRequestNumber,
        retries: Int = 10
    ): F[MergeRequestOut] =
      client
        .get[MergeRequestOut](url.existingMergeRequest(repo, number), modify(repo))
        .flatMap {
          case mr if mr.mergeStatus =!= GitLabMergeStatus.Checking => F.pure(mr)
          case _ if retries > 0                                    => waitForMergeRequestStatus(number, retries - 1)
          case other                                               => F.pure(other)
        }

    val updatedMergeRequest =
      if (!config.mergeWhenPipelineSucceeds)
        mergeRequest
      else
        mergeRequest
          .flatMap(mr => waitForMergeRequestStatus(mr.iid))
          .flatMap {
            case mr if mr.mergeStatus === GitLabMergeStatus.CanBeMerged =>
              for {
                _ <- logger.info(s"Setting ${mr.webUrl} to merge when pipeline succeeds")
                res <-
                  client
                    .put[MergeRequestOut](
                      url.mergeWhenPiplineSucceeds(repo, mr.iid),
                      modify(repo)
                    )
                    // it's possible that our status changed from can be merged already,
                    // so just handle it gracefully and proceed without setting auto merge.
                    .recoverWith { case UnexpectedResponse(_, _, _, status, _) =>
                      logger
                        .warn(s"Unexpected gitlab response setting auto merge: $status")
                        .as(mr)
                    }
              } yield res
            case mr =>
              logger.info(s"Unable to automatically merge ${mr.webUrl}").map(_ => mr)
          }

    updatedMergeRequest.map(_.pullRequestOut)
  }

  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    client
      .putWithBody[MergeRequestOut, UpdateState](
        url.existingMergeRequest(repo, number),
        UpdateState(PullRequestState.Closed),
        modify(repo)
      )
      .map(_.pullRequestOut)

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.getBranch(repo, branch), modify(repo))

  override def getRepo(repo: Repo): F[RepoOut] =
    client.get(url.repos(repo), modify(repo))

  override def referencePullRequest(number: PullRequestNumber): String =
    s"!${number.value}"

  // https://docs.gitlab.com/ee/api/notes.html#create-new-merge-request-note
  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] =
    client.postWithBody(url.comments(repo, number), Comment(comment), modify(repo))

}
