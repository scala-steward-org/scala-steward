package org.scalasteward.core.bitbucket.http4s

import io.circe.{Decoder}
import org.http4s.Uri
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.util.uri.uriDecoder
import org.scalasteward.core.vcs.data.{BranchOut, CommitOut, PullRequestOut, PullRequestState}

private[http4s] object json {
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
}
