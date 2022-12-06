package org.scalasteward.core.vcs.bitbucket
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final private[bitbucket] case class DefaultReviewers(values: List[Reviewer])
private[bitbucket] object DefaultReviewers {
  implicit val defaultReviewersCodec: Codec[DefaultReviewers] = deriveCodec
}

final private[bitbucket] case class Reviewer(uuid: String)
private[bitbucket] object Reviewer {
  implicit val reviewerCodec: Codec[Reviewer] = deriveCodec
}
