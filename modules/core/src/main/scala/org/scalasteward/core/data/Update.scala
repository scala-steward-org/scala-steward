/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.data

import cats.implicits._
import eu.timepit.refined.W
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.data.Update.{Group, Single}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import org.scalasteward.core.util.string.MinLengthString

sealed trait Update extends Product with Serializable {
  def groupId: GroupId
  def artifactId: String
  def artifactIds: Nel[String]
  def currentVersion: String
  def newerVersions: Nel[String]

  final def name: String =
    Update.nameOf(groupId, artifactId)

  final def nextVersion: String =
    newerVersions.head

  final def show: String = {
    val artifacts = this match {
      case s: Single => s.artifactId + s.configurations.fold("")(":" + _)
      case g: Group  => g.artifactIds.mkString_("{", ", ", "}")
    }
    val versions = (currentVersion :: newerVersions).mkString_("", " -> ", "")
    s"$groupId:$artifacts : $versions"
  }
}

object Update {
  final case class Single(
      groupId: GroupId,
      artifactId: String,
      currentVersion: String,
      newerVersions: Nel[String],
      configurations: Option[String] = None,
      newerGroupId: Option[GroupId] = None
  ) extends Update {
    override def artifactIds: Nel[String] =
      Nel.one(artifactId)
  }

  final case class Group(
      groupId: GroupId,
      artifactIds: Nel[String],
      currentVersion: String,
      newerVersions: Nel[String]
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
        val artifacts = nel.map(_.artifactId).distinct.sorted
        if (artifacts.tail.nonEmpty)
          Group(head.groupId, artifacts, head.currentVersion, head.newerVersions)
        else
          head
      }
      .toList
      .sortBy(update => (update.groupId, update.artifactId))

  val commonSuffixes: List[String] =
    List("config", "contrib", "core", "extra", "server")

  def nameOf(groupId: GroupId, artifactId: String): String =
    if (commonSuffixes.contains(artifactId))
      util.string.rightmostLabel(groupId.value)
    else
      artifactId

  implicit val updateEncoder: Encoder[Update] =
    io.circe.generic.semiauto.deriveEncoder

  implicit val updateDecoder: Decoder[Update] =
    io.circe.generic.semiauto.deriveDecoder

  implicit val updateSingleEncoder: Encoder[Update.Single] =
    io.circe.generic.semiauto.deriveEncoder

  implicit val updateSingleDecoder: Decoder[Update.Single] =
    io.circe.generic.semiauto.deriveDecoder
}
