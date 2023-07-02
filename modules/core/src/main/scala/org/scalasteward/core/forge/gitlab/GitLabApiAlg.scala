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

package org.scalasteward.core.forge.gitlab

import cats.effect.Temporal
import cats.Parallel
import cats.syntax.all._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s.{Request, Status, Uri}
import org.scalasteward.core.application.Config.{ForgeCfg, GitLabCfg}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.data._
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.util.uri.uriDecoder
import org.scalasteward.core.util.{intellijThisImportIsUsed, HttpJsonClient, UnexpectedResponse}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.{Duration, DurationInt}

final private[gitlab] case class ForkPayload(id: String, namespace: String)
final private[gitlab] case class MergeRequestPayload(
    id: String,
    title: String,
    description: String,
    labels: Option[List[String]],
    assignee_ids: Option[List[Int]],
    reviewer_ids: Option[List[Int]],
    target_project_id: Long,
    remove_source_branch: Option[Boolean],
    source_branch: String,
    target_branch: Branch
)

private[gitlab] object MergeRequestPayload {
  def apply(
      id: String,
      projectId: Long,
      data: NewPullRequestData,
      usernamesToUserIdsMapping: Map[String, Int],
      removeSourceBranch: Boolean
  ): MergeRequestPayload = {
    val assignees = data.assignees.flatMap(usernamesToUserIdsMapping.get)
    val reviewers = data.reviewers.flatMap(usernamesToUserIdsMapping.get)
    MergeRequestPayload(
      id = id,
      title = List(if (data.draft) "Draft: " else "", data.title).mkString,
      description = data.body,
      assignee_ids = Option.when(assignees.nonEmpty)(assignees),
      reviewer_ids = Option.when(reviewers.nonEmpty)(reviewers),
      labels = Option.when(data.labels.nonEmpty)(data.labels),
      target_project_id = projectId,
      remove_source_branch = Option.when(removeSourceBranch)(removeSourceBranch),
      source_branch = data.head,
      target_branch = data.base
    )
  }
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

final private[gitlab] case class MergeRequestApprovalsOut(
    approvalsRequired: Int
)

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
  intellijThisImportIsUsed(uriDecoder)

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

  implicit val mergeRequestApprovalsOutDecoder: Decoder[MergeRequestApprovalsOut] =
    Decoder.instance { c =>
      for {
        requiredReviewers <- c.downField("approvals_required").as[Int]
      } yield MergeRequestApprovalsOut(requiredReviewers)
    }

  implicit val projectIdDecoder: Decoder[ProjectId] = deriveDecoder
  implicit val mergeRequestPayloadEncoder: Encoder[MergeRequestPayload] =
    deriveEncoder[MergeRequestPayload].mapJson(_.dropNullValues)

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

final class GitLabApiAlg[F[_]: Parallel](
    forgeCfg: ForgeCfg,
    gitLabCfg: GitLabCfg,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: Temporal[F]
) extends ForgeApiAlg[F] {
  import GitLabJsonCodec._

  private val url = new Url(forgeCfg.apiHost)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client.get(url.listMergeRequests(repo, head, base.name), modify(repo))

  override def createFork(repo: Repo): F[RepoOut] = {
    val userOwnedRepo = repo.copy(owner = forgeCfg.login)
    val data = ForkPayload(url.encodedProjectId(userOwnedRepo), forgeCfg.login)
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
    val targetRepo = if (forgeCfg.doNotFork) repo else repo.copy(owner = forgeCfg.login)
    val mergeRequest = for {
      projectId <- client.get[ProjectId](url.repos(repo), modify(repo))
      usernameMapping <- getUsernameToUserIdsMapping(repo, (data.assignees ++ data.reviewers).toSet)
      payload = MergeRequestPayload(
        id = url.encodedProjectId(targetRepo),
        projectId = projectId.id,
        data = data,
        usernamesToUserIdsMapping = usernameMapping,
        removeSourceBranch = gitLabCfg.removeSourceBranch
      )
      res <- client.postWithBody[MergeRequestOut, MergeRequestPayload](
        uri = url.mergeRequest(targetRepo),
        body = payload,
        modify = modify(repo)
      )
    } yield res

    def waitForMergeRequestStatus(
        number: PullRequestNumber,
        retries: Int = 10,
        initialDelay: Duration = 100.milliseconds,
        backoffMultiplier: Double = 2.0
    ): F[MergeRequestOut] =
      client
        .get[MergeRequestOut](url.existingMergeRequest(repo, number), modify(repo))
        .flatMap {
          case mr if mr.mergeStatus =!= GitLabMergeStatus.Checking => F.pure(mr)
          case mr if retries > 0 =>
            logger.info(
              s"Merge request is still in '${mr.mergeStatus}' state. We will check merge request status in $initialDelay again. " +
                s"Remaining retries count is $retries"
            ) >> F.sleep(initialDelay) >> waitForMergeRequestStatus(
              number,
              retries - 1,
              initialDelay * backoffMultiplier
            )
          case mr =>
            logger
              .warn(
                s"Exhausted all retries while waiting for merge request status. Last known status is '${mr.mergeStatus}'"
              )
              .as(mr)
        }

    val updatedMergeRequest =
      if (!gitLabCfg.mergeWhenPipelineSucceeds)
        mergeRequest
      else {
        for {
          mr <- mergeRequest
          mrWithStatus <- waitForMergeRequestStatus(mr.iid)
          _ <- maybeSetReviewers(repo, mrWithStatus)
          mergedUponSuccess <- mergePipelineUponSuccess(repo, mrWithStatus)
        } yield mergedUponSuccess
      }

    updatedMergeRequest.map(_.pullRequestOut)
  }

  override def updatePullRequest(
      number: PullRequestNumber,
      repo: Repo,
      data: NewPullRequestData
  ): F[Unit] =
    logger.warn("Updating PRs is not yet supported for GitLab")

  private def mergePipelineUponSuccess(repo: Repo, mr: MergeRequestOut): F[MergeRequestOut] =
    mr match {
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

  private def maybeSetReviewers(repo: Repo, mrOut: MergeRequestOut): F[MergeRequestOut] =
    gitLabCfg.requiredReviewers match {
      case Some(requiredReviewers) =>
        for {
          _ <- logger.info(
            s"Setting number of required reviewers on ${mrOut.webUrl} to $requiredReviewers"
          )
          _ <-
            client
              .put[MergeRequestApprovalsOut](
                url.requiredApprovals(repo, mrOut.iid, requiredReviewers),
                modify(repo)
              )
              .map(_ => ())
              .recoverWith { case UnexpectedResponse(_, _, _, status, body) =>
                logger
                  .warn(s"Unexpected response setting required reviewers: $status:  $body")
                  .as(())
              }
        } yield mrOut
      case None => F.pure(mrOut)
    }

  private def getUsernameToUserIdsMapping(repo: Repo, usernames: Set[String]): F[Map[String, Int]] =
    usernames.toList
      .parTraverse { username =>
        getUserIdForUsername(repo, username).map { userIdOpt =>
          userIdOpt.map(userId => (username, userId))
        }
      }
      .map(_.flatten.toMap)

  private def getUserIdForUsername(repo: Repo, username: String): F[Option[Int]] = {
    val userIdOrError: F[Decoder.Result[Int]] = client
      .get[Json](url.users.withQueryParam("username", username), modify(repo))
      .flatMap { usersReponse =>
        usersReponse.hcursor.values match {
          case Some(users) =>
            users.headOption match {
              case Some(user) => F.pure(user.hcursor.get[Int]("id"))
              case None       => F.raiseError(new RuntimeException("user not found"))
            }
          case None =>
            F.raiseError(
              new RuntimeException(
                s"unexpected response from api, Json array expected: $usersReponse"
              )
            )
        }
      }

    F.rethrow(userIdOrError)
      .map(Option(_))
      .handleErrorWith { error =>
        logger.error(error)(s"failed to get mappings for user '$username'").as(none[Int])
      }
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
