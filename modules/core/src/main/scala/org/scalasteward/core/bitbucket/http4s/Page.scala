package org.scalasteward.core.bitbucket.http4s

import io.circe.Decoder

final private[http4s] case class Page[A](values: List[A])

private[http4s] object Page {
  implicit def pageDecoder[A: Decoder]: Decoder[Page[A]] = Decoder.instance { c =>
    c.downField("values").as[List[A]].map(Page(_))
  }
}
