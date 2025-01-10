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

package org.scalasteward.core.forge.bitbucketserver

import cats.ApplicativeThrow
import cats.data.NonEmptyList
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Uri
import org.scalasteward.core.forge.data.*
import org.scalasteward.core.git
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.util.intellijThisImportIsUsed
import org.scalasteward.core.util.uri.uriDecoder

object Json {
  case class Page[A](values: List[A])

  case class Repo(slug: String, links: Links) {
    def cloneUrlOrRaise[F[_]](implicit F: ApplicativeThrow[F]): F[Uri] =
      links
        .get("clone")
        .flatMap(_.find(_.name.contains("http")).map(_.href))
        .fold(F.raiseError[Uri](new Throwable(s"$links does not contain a HTTP clone URL")))(F.pure)
  }

  case class Project(key: String)

  type Links = Map[String, NonEmptyList[Link]]

  case class Link(href: Uri, name: Option[String])

  case class PR(
      id: PullRequestNumber,
      version: Int,
      title: String,
      state: PullRequestState,
      links: Links
  ) {
    def htmlUrl: Uri =
      links("self").head.href

    def toPullRequestOut: PullRequestOut =
      PullRequestOut(htmlUrl, state, id, title)
  }

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

  case class Condition(reviewers: List[DefaultReviewer])

  case class DefaultReviewer(name: String)

  case class Reviewer(user: User)

  case class User(name: String)

  case class Branches(values: NonEmptyList[Branch])

  case class Branch(displayId: git.Branch, latestCommit: Sha1) {
    def toBranchOut: BranchOut =
      BranchOut(displayId, CommitOut(latestCommit))
  }

  case class Comment(text: String)

  implicit val branchDecoder: Decoder[Branch] = deriveDecoder
  implicit val branchesDecoder: Decoder[Branches] = deriveDecoder
  implicit val commentCodec: Codec[Comment] = deriveCodec
  implicit val conditionDecoder: Decoder[Condition] = deriveDecoder
  implicit val defaultReviewerDecoder: Decoder[DefaultReviewer] = deriveDecoder
  implicit val linkDecoder: Decoder[Link] = deriveDecoder
  implicit val newPREncoder: Encoder[NewPR] = deriveEncoder
  implicit def pageDecoder[A: Decoder]: Decoder[Page[A]] = deriveDecoder
  implicit val prDecoder: Decoder[PR] = deriveDecoder
  implicit val projectCodec: Codec[Project] = deriveCodec
  implicit val refEncoder: Encoder[Ref] = deriveEncoder
  implicit val repoDecoder: Decoder[Repo] = deriveDecoder
  implicit val repositoryEncoder: Encoder[Repository] = deriveEncoder
  implicit val reviewerCodec: Codec[Reviewer] = deriveCodec
  implicit val userCodec: Codec[User] = deriveCodec

  intellijThisImportIsUsed(uriDecoder)
}
