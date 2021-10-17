package org.scalasteward.core.mavencentral

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId}

import java.time.Instant

case class SearchOut(response: SearchResponse)
object SearchOut {
  implicit val decoder: Decoder[SearchOut] = deriveDecoder
}

case class SearchResponse(docs: Seq[Document])
object SearchResponse {
  implicit val decoder: Decoder[SearchResponse] = deriveDecoder
}

//"g": "software.amazon.awssdk",
//  "a": "s3",
//  "v": "2.17.71",
case class Document(
  g: String,
  a: String,
  v: String,
  timestamp: Instant
) {
  val groupId = GroupId(g)
  val artifactId = ArtifactId(a)
  val dependency = Dependency(groupId, artifactId, v)
}

object Document {
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochMilli)

  implicit val decoder: Decoder[Document] = deriveDecoder
}


