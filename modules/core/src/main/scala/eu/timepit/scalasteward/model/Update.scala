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

import scala.util.matching.Regex

sealed trait Update {
  def artifactId: String
  def currentVersion: String
  def groupId: String
  def name: String
  def nextVersion: String
  def replaceAllIn(str: String): Option[String]
  def show: String
}

object Update {
  final case class Single(
      groupId: String,
      artifactId: String,
      currentVersion: String,
      newerVersions: NonEmptyList[String]
  ) extends Update {
    override def name: String =
      if (meaninglessSuffixes.contains(artifactId))
        groupId.split('.').lastOption.getOrElse(groupId)
      else
        artifactId

    override def nextVersion: String =
      newerVersions.head

    override def replaceAllIn(str: String): Option[String] = {
      def normalize(searchTerm: String): String =
        Regex
          .quoteReplacement(removeMeaninglessSuffixes(searchTerm))
          .replace("-", ".?")

      val regex = s"(?i)(${normalize(name)}.*?)${Regex.quote(currentVersion)}".r
      var updated = false
      val result = regex.replaceAllIn(str, m => {
        updated = true
        m.group(1) + nextVersion
      })
      if (updated) Some(result) else None
    }

    override def show: String =
      s"$groupId:$artifactId : ${(currentVersion :: newerVersions).mkString_("", " -> ", "")}"
  }

  final case class Group(
      updates: NonEmptyList[Single]
  ) extends Update {
    override def artifactId: String =
      updates.head.artifactId

    override def currentVersion: String =
      updates.head.currentVersion

    override def groupId: String =
      updates.head.groupId

    override def name: String =
      updates.head.name

    override def nextVersion: String =
      updates.head.nextVersion

    override def replaceAllIn(str: String): Option[String] =
      (updates.map(_.replaceAllIn(str)) :+ replaceAllIn2(str)).find(_.isDefined).flatten

    override def show: String =
      updates.map(_.show).mkString_("", ", ", "")

    def commonArtifactPrefix: Option[String] = {
      val list = updates.map(_.artifactId).toList
      val prefix = list.foldLeft("") { (_, _) =>
        (list.min.view, list.max.view).zipped.takeWhile(v => v._1 == v._2).unzip._1.mkString
      }
      if (prefix.isEmpty) None else Some(prefix)
    }

    def replaceAllIn2(str: String): Option[String] = {
      def normalize(searchTerm: String): String =
        Regex
          .quoteReplacement(removeMeaninglessSuffixes(searchTerm))
          .replace("-", ".?")

      val regex =
        s"(?i)(${normalize(commonArtifactPrefix.getOrElse(updates.head.artifactId))}.*?)${Regex
          .quote(currentVersion)}".r
      var updated = false
      val result = regex.replaceAllIn(str, m => {
        updated = true
        m.group(1) + nextVersion
      })
      if (updated) Some(result) else None
    }
  }

  ///

  def fromString(str: String): Either[Throwable, Single] =
    Either.catchNonFatal {
      val regex = """([^\s:]+):([^\s:]+)[^\s]*\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split("->").map(_.trim).toList)
          Single(groupId, artifactId, version, newerVersions)
      }
    }

  val meaninglessSuffixes: List[String] =
    List("core", "server")

  def removeMeaninglessSuffixes(str: String): String =
    meaninglessSuffixes
      .map("-" + _)
      .find(suffix => str.endsWith(suffix))
      .fold(str)(suffix => str.substring(0, str.length - suffix.length))
}
