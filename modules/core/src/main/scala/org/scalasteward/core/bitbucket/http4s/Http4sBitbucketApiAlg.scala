package org.scalasteward.core.bitbucket.http4s

import cats.Monad
import org.http4s.{Request, Uri}
import cats.effect.Sync
import org.scalasteward.core.bitbucket.Url
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.{HttpJsonClient, MonadThrowable}
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data.{
  BranchOut,
  NewPullRequestData,
  PullRequestOut,
  PullRequestState,
  Repo,
  RepoOut,
  UserOut
}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import cats.implicits._
import org.scalasteward.core.bitbucket.http4s.Http4sBitbucketApiAlg.Page
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.git.Sha1._
import org.scalasteward.core.util.uri._
import org.scalasteward.core.vcs.data.CommitOut
import org.http4s.client.UnexpectedStatus
import org.http4s.Status
import org.scalasteward.core.vcs.data.AuthenticatedUser

object Http4sBitbucketApiAlg {
  case class Page[A](values: List[A])
  object Page {
    implicit def pageDecoder[A: Decoder]: Decoder[Page[A]] = Decoder.instance { c =>
      c.downField("values").as[List[A]].map(Page(_))
    }
  }
}

class Http4sBitbucketApiAlg[F[_]: Sync](
    bitbucketApiHost: Uri,
    user: AuthenticatedUser,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit client: HttpJsonClient[F], E: MonadThrowable[F])
    extends VCSApiAlg[F] {

  implicit private val repoDecoder = Decoder.instance { c =>
    c.as[String].map(_.split('/')).flatMap { parts =>
      parts match {
        case Array(owner, name) => Repo(owner, name).asRight
        case _                  => DecodingFailure("Repo", c.history).asLeft
      }
    }
  }
  private val parentRepoNameDecoder = Decoder
    .instance(_.downField("parent").downField("full_name").as[Option[Repo]])

  implicit val repoOutDecoder: Decoder[RepoOut] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      owner <- c
        .downField("owner")
        .downField("username")
        .as[String]
        .orElse(c.downField("owner").downField("nickname").as[String])
      cloneUrl <- c
        .downField("links")
        .downField("clone")
        .downAt { p =>
          p.asObject
            .flatMap(o => o("name"))
            .flatMap(_.asString)
            .contains("https")
        }
        .downField("href")
        .as[Uri]
      defaultBranch <- c.downField("mainbranch").downField("name").as[Branch]
    } yield RepoOut(name, UserOut(owner), None, cloneUrl, defaultBranch)
  }

  implicit val branchOutDecoder: Decoder[BranchOut] = Decoder.instance { c =>
    for {
      branch <- c.downField("name").as[Branch]
      commitHash <- c.downField("target").downField("hash").as[Sha1]
    } yield BranchOut(branch, CommitOut(commitHash))
  }

  implicit val pullRequestStateDecoder: Decoder[PullRequestState] =
    Decoder[String].emap {
      case "OPEN"                               => Right(PullRequestState.Open)
      case "MERGED" | "SUPERSEDED" | "DECLINED" => Right(PullRequestState.Closed)
      case unknown                              => Left(s"Unexpected string '$unknown'")
    }

  implicit val pullRequestOutDecoder: Decoder[PullRequestOut] = Decoder.instance { c =>
    for {
      title <- c.downField("title").as[String]
      state <- c.downField("state").as[PullRequestState]
      html_url <- c.downField("links").downField("self").downField("href").as[Uri]
    } yield (PullRequestOut(html_url, state, title))
  }

  implicit val newPullRequestData: Encoder[NewPullRequestData] = Encoder.instance { d =>
    Json.obj(
      ("title", Json.fromString(d.title)),
      (
        "source",
        Json.obj(
          ("branch", Json.obj(("name", Json.fromString(d.sourceBranch.name)))),
          (
            "repository",
            Json.obj(("full_name", Json.fromString(s"${d.sourceRepo.owner}/${d.sourceRepo.repo}")))
          )
        )
      ),
      ("description", Json.fromString(d.body)),
      (
        "destination",
        Json.obj(
          ("branch", Json.obj(("name", Json.fromString(d.destinationBranch.name))))
        )
      )
    )
  }

  val url = new Url(bitbucketApiHost)

  override def createFork(repo: Repo): F[RepoOut] =
    for {
      forkJson <- client
        .post[Json](url.forks(repo), modify(repo))
        .handleErrorWith {
          case UnexpectedStatus(Status.BadRequest) =>
            client.get(url.repo(repo.copy(owner = user.login)), modify(repo))
        }
      fork <- decodeJsonF[RepoOut](forkJson)
      maybeParentRepo <- decodeJsonF(forkJson)(parentRepoNameDecoder)
      maybeParentRepoOut <- maybeParentRepo
        .map(n => client.get[RepoOut](url.repo(n), modify(n)))
        .sequence[F, RepoOut]
    } yield (fork.copy(parent = maybeParentRepoOut))

  override def createPullRequest(repo: Repo, data: NewPullRequestData)(
      implicit F: Monad[F]
  ): F[PullRequestOut] =
    client.postWithBody(url.pullRequests(repo), data, modify(repo))

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branch(repo, branch), modify(repo))

  override def getRepo(repo: Repo): F[RepoOut] =
    for {
      json <- client.get[Json](url.repo(repo), modify(repo))
      repoOut <- decodeJsonF[RepoOut](json)
      maybeParentRepo <- decodeJsonF(json)(parentRepoNameDecoder)
      maybeParentRepoOut <- maybeParentRepo
        .map(n => client.get[RepoOut](url.repo(n), modify(n)))
        .sequence[F, RepoOut]
    } yield repoOut.copy(parent = maybeParentRepoOut)

  private def decodeJsonF[A](json: Json)(implicit d: Decoder[A]) =
    json.as[A] match {
      case Left(_)      => E.raiseError(new Exception)
      case Right(value) => value.pure[F]
    }

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Page[PullRequestOut]](url.listPullRequests(repo, head), modify(repo))
      .map(_.values)

}
