/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.model

import cats.data.NonEmptyList
import cats.implicits._
import eu.timepit.refined.W
import eu.timepit.scalasteward.model.Update.{Group, Single}
import eu.timepit.scalasteward.util
import eu.timepit.scalasteward.util.string.MinLengthString
import io.circe.{Decoder, Encoder}
import scala.util.matching.Regex

sealed trait Update extends Product with Serializable {
  def groupId: String
  def artifactId: String
  def artifactIds: NonEmptyList[String]
  def currentVersion: String
  def newerVersions: NonEmptyList[String]

  def name: String =
    Update.nameOf(groupId, artifactId)

  def nextVersion: String =
    newerVersions.head

  def replaceAllIn(target: String): Option[String] = {
    val quotedSearchTerms = searchTerms
      .map { term =>
        Regex
          .quoteReplacement(Update.removeCommonSuffix(term))
          .replace("-", ".?")
      }
      .filter(_.nonEmpty)
    val searchTerm = quotedSearchTerms.mkString_("(", "|", ")")
    val regex = s"(?i)($searchTerm.*?)${Regex.quote(currentVersion)}".r
    var updated = false
    val result = regex.replaceAllIn(target, m => {
      updated = true
      m.group(1) + nextVersion
    })
    if (updated) Some(result) else None
  }

  def searchTerms: NonEmptyList[String] = {
    val terms = this match {
      case s: Single => s.artifactIds
      case g: Group  => g.artifactIds.concat(g.artifactIdsPrefix.map(_.value).toList)
    }
    terms.map(Update.nameOf(groupId, _))
  }

  def show: String = {
    val artifacts = this match {
      case s: Single => s.artifactId
      case g: Group  => g.artifactIds.mkString_("{", ", ", "}")
    }
    val versions = (currentVersion :: newerVersions).mkString_("", " -> ", "")
    s"$groupId:$artifacts : $versions"
  }
}

object Update {
  final case class Single(
      groupId: String,
      artifactId: String,
      currentVersion: String,
      newerVersions: NonEmptyList[String]
  ) extends Update {
    override def artifactIds: NonEmptyList[String] =
      NonEmptyList.one(artifactId)
  }

  final case class Group(
      groupId: String,
      artifactIds: NonEmptyList[String],
      currentVersion: String,
      newerVersions: NonEmptyList[String]
  ) extends Update {
    override def artifactId: String = {
      val possibleMainArtifactIds = for {
        prefix <- artifactIdsPrefix.toList
        suffix <- commonSuffixes
      } yield prefix.value + suffix

      artifactIds
        .find(artifactId => possibleMainArtifactIds.contains(artifactId))
        .getOrElse(artifactIds.head)
    }

    def artifactIdsPrefix: Option[MinLengthString[W.`3`.T]] =
      util.string.longestCommonPrefixGreater[W.`3`.T](artifactIds)
  }

  ///

  def group(updates: List[Single]): List[Update] =
    updates
      .groupByNel(update => (update.groupId, update.currentVersion, update.newerVersions))
      .values
      .map { nel =>
        val head = nel.head
        if (nel.tail.nonEmpty)
          Group(head.groupId, nel.map(_.artifactId).sorted, head.currentVersion, head.newerVersions)
        else
          head
      }
      .toList

  def fromString(str: String): Either[Throwable, Single] =
    Either.catchNonFatal {
      val regex = """([^\s:]+):([^\s:]+)[^\s]*\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split("->").map(_.trim).toList)
          Single(groupId, artifactId, version, newerVersions)
      }
    }

  val commonSuffixes: List[String] =
    List("config", "contrib", "core", "extra", "server")

  def removeCommonSuffix(str: String): String =
    util.string.removeSuffix(str, commonSuffixes)

  def nameOf(groupId: String, artifactId: String): String =
    if (commonSuffixes.contains(artifactId))
      groupId.split('.').lastOption.getOrElse(groupId)
    else
      artifactId

  implicit val updateEncoder: Encoder[Update] =
    io.circe.generic.semiauto.deriveEncoder

  implicit val updateDecoder: Decoder[Update] =
    io.circe.generic.semiauto.deriveDecoder
}
