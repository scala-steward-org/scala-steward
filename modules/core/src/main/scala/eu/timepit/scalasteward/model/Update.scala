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
  def artifactIds: NonEmptyList[String]
  def currentVersion: String
  def newerVersions: NonEmptyList[String]

  def name: String =
    if (Update.commonSuffixes.contains(artifactId))
      groupId.split('.').lastOption.getOrElse(groupId)
    else
      artifactId

  def nextVersion: String =
    newerVersions.head

  def replaceAllIn(target: String): Option[String] = {
    val quotedSearchTerms = searchTerms.map { term =>
      Regex
        .quoteReplacement(Update.removeCommonSuffix(term))
        .replace("-", ".?")
    }
    val searchTerm = quotedSearchTerms.mkString_("(", "|", ")")
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
      case s: Single => s.artifactIds
      case g: Group  => g.artifactIds.concat(g.artifactIdsPrefix.map(_.value).toList)
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
    override def artifactId: String =
      artifactIds.head

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

  def ignore(update: Update): Boolean =
    update.groupId match {
      case "org.scala-lang" =>
        update.artifactIds.exists {
          case "scala-compiler" => true
          case "scala-library"  => true
          case _                => false
        }
      case _ => false
    }
}
