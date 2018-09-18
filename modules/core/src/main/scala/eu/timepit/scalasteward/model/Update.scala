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
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.scalasteward.model.Update.{Group, Single}
import eu.timepit.scalasteward.util

import scala.util.matching.Regex

sealed trait Update extends Product with Serializable {
  def groupId: String
  def artifactId: String
  def currentVersion: String
  def newerVersions: NonEmptyList[String]

  def name: String
  def show: String

  def nextVersion: String =
    newerVersions.head

  def replaceAllIn(target: String): Option[String] = {
    val searchTerm = searchTerms
      .map { term =>
        Regex
          .quoteReplacement(Update.removeCommonSuffix(term))
          .replace("-", ".?")
      }
      .mkString_("(", "|", ")")

    val regex = s"(?i)($searchTerm.*?)${Regex.quote(currentVersion)}".r
    var updated = false
    val result = regex.replaceAllIn(target, m => {
      updated = true
      m.group(1) + nextVersion
    })
    if (updated) Some(result) else None
  }

  def searchTerms: NonEmptyList[String] =
    this match {
      case s: Single => NonEmptyList.one(s.artifactId)
      case g: Group  => g.artifactIds.concat(g.artifactIdsPrefix.map(_.value).toList)
    }
}

object Update {
  final case class Single(
      groupId: String,
      artifactId: String,
      currentVersion: String,
      newerVersions: NonEmptyList[String]
  ) extends Update {
    override def name: String =
      if (commonSuffixes.contains(artifactId))
        groupId.split('.').lastOption.getOrElse(groupId)
      else
        artifactId

    override def show: String =
      s"$groupId:$artifactId : ${(currentVersion :: newerVersions).mkString_("", " -> ", "")}"
  }

  final case class Group(
      groupId: String,
      currentVersion: String,
      newerVersions: NonEmptyList[String],
      updates: NonEmptyList[Single]
  ) extends Update {
    def artifactIds: NonEmptyList[String] =
      updates.map(_.artifactId)

    override def artifactId: String =
      updates.head.artifactId

    override def name: String =
      updates.head.name

    override def show: String =
      updates.map(_.show).mkString_("", ", ", "")

    def artifactIdsPrefix: Option[NonEmptyString] =
      util.longestCommonNonEmptyPrefix(artifactIds)
  }

  ///

  def apply(
      groupId: String,
      artifactId: String,
      currentVersion: String,
      newerVersions: NonEmptyList[String]
  ): Single =
    Single(groupId, artifactId, currentVersion, newerVersions)

  def fromString(str: String): Either[Throwable, Single] =
    Either.catchNonFatal {
      val regex = """([^\s:]+):([^\s:]+)[^\s]*\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split("->").map(_.trim).toList)
          Update(groupId, artifactId, version, newerVersions)
      }
    }

  val commonSuffixes: List[String] =
    List("core", "server")

  def removeCommonSuffix(str: String): String =
    util.removeSuffix(str, commonSuffixes)
}
