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

package org.scalasteward.core.forge.azurerepos

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import org.http4s.Uri
import org.scalasteward.core.forge.data.*
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.util.unexpectedString
import org.scalasteward.core.util.uri.uriDecoder

final private[azurerepos] case class PullRequestPayload(
    sourceRefName: String,
    targetRefName: String,
    title: String,
    labels: Option[List[String]],
    description: String
)

private[azurerepos] object PullRequestPayload {
  val withPrefix: String => String = name => s"refs/heads/$name"

  def from(data: NewPullRequestData): PullRequestPayload =
    PullRequestPayload(
      sourceRefName = withPrefix(data.head),
      targetRefName = withPrefix(data.base.name),
      title = data.title,
      labels = Option.when(data.labels.nonEmpty)(data.labels),
      description = data.body
    )
}

final private[azurerepos] case class ClosePullRequestPayload(status: String)

final private case class AzureComment(content: String)

final private[azurerepos] case class PullRequestCommentPayload(
    comments: List[AzureComment],
    status: Int = 1
)

private[azurerepos] object PullRequestCommentPayload {

  def createComment(content: String): PullRequestCommentPayload =
    PullRequestCommentPayload(List(AzureComment(content)))
}

final private[azurerepos] case class Paginated[A](value: List[A])

private[azurerepos] object Paginated {
  implicit def pageDecoder[A: Decoder]: Decoder[Paginated[A]] =
    Decoder.instance { c =>
      c.downField("value").as[List[A]].map(Paginated(_))
    }
}
private[azurerepos] object JsonCodec {

  implicit val repoOutDecoder: Decoder[RepoOut] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      owner <-
        c.downField("project")
          .downField("name")
          .as[String]
          .map(UserOut(_))
      cloneUrl <- c.downField("remoteUrl").as[Uri]
      parent <-
        c.downField("parentRepository")
          .as[Option[RepoOut]]
      defaultBranch <-
        c.downField("defaultBranch")
          .as[Option[String]]
          .map(_.getOrElse("master"))
          .map(Branch(_))
    } yield RepoOut(name, owner, parent, cloneUrl, defaultBranch)
  }

  implicit val branchOutDecoder: Decoder[BranchOut] = Decoder.instance { c =>
    for {
      branch <- c.downField("name").as[Branch]
      commitHash <- c.downField("commit").downField("commitId").as[Sha1]
    } yield BranchOut(branch, CommitOut(commitHash))
  }

  implicit val pullRequestStateDecoder: Decoder[PullRequestState] =
    Decoder[String].emap {
      case "active"                  => Right(PullRequestState.Open)
      case "completed" | "abandoned" => Right(PullRequestState.Closed)
      case s => unexpectedString(s, List("active", "completed", "abandoned"))
    }

  implicit val pullRequestOutDecoder: Decoder[PullRequestOut] = Decoder.instance { c =>
    for {
      title <- c.downField("title").as[String]
      number <- c.downField("pullRequestId").as[PullRequestNumber]
      state <- c.downField("status").as[PullRequestState]
      url <- c.downField("url").as[Uri]
    } yield PullRequestOut(url, state, number, title)
  }

  implicit val commentDecoder: Decoder[Comment] = Decoder.instance { c =>
    c.downField("comments").downN(0).downField("content").as[String].map(Comment(_))
  }

  implicit val pullRequestPayloadEncoder: Encoder[PullRequestPayload] = deriveEncoder
  implicit val closePullRequestPayloadEncoder: Encoder[ClosePullRequestPayload] = deriveEncoder
  implicit val pullRequestCommentEncoder: Encoder[AzureComment] = deriveEncoder
  implicit val pullRequestCommentPayloadEncoder: Encoder[PullRequestCommentPayload] = deriveEncoder

}
