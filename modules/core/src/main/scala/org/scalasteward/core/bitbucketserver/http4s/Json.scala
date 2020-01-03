package org.scalasteward.core.bitbucketserver.http4s

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.Uri
import org.scalasteward.core.vcs.data.PullRequestState

object Json {
  case class Page[A](values: List[A])

  case class Repo(id: Int, name: String, forkable: Boolean, project: Project, links: Links)

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

  case class Condition(reviewers: List[DefaultReviewer])

  case class DefaultReviewer(name: String)

  case class Reviewer(user: User)

  case class User(name: String)

  implicit def pageDecode[A: Decoder]: Decoder[Page[A]] = deriveDecoder
  implicit val repoDecode: Decoder[Repo] = deriveDecoder
  implicit val projectDecode: Decoder[Project] = deriveDecoder
  implicit val linkDecoder: Decoder[Link] = deriveDecoder
  implicit val uriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri.unsafeFromString)
  implicit val prDecoder: Decoder[PR] = deriveDecoder
  implicit val reviewerDecoder: Decoder[Reviewer] = deriveDecoder
  implicit val userDecoder: Decoder[User] = deriveDecoder
  implicit val defaultReviewerDecoder: Decoder[DefaultReviewer] = deriveDecoder
  implicit val conditionDecoder: Decoder[Condition] = deriveDecoder

  implicit val encodeNewPR: Encoder[NewPR] = deriveEncoder
  implicit val encodeRef: Encoder[Ref] = deriveEncoder
  implicit val encodeRepository: Encoder[Repository] = deriveEncoder
  implicit val encodeProject: Encoder[Project] = deriveEncoder
  implicit val encodeReviewer: Encoder[Reviewer] = deriveEncoder
  implicit val encodeUser: Encoder[User] = deriveEncoder
}
