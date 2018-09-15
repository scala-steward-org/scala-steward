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

final case class Update(
    groupId: String,
    artifactId: String,
    currentVersion: String,
    newerVersions: NonEmptyList[String]
) {

  /** Returns true if the changes of applying `other` would include the changes
    * of applying `this`.
    */
  def isImpliedBy(other: Update): Boolean =
    groupId === other.groupId &&
      artifactId =!= other.artifactId &&
      artifactId.startsWith(Update.removeIgnorableSuffix(other.artifactId)) &&
      currentVersion === other.currentVersion &&
      newerVersions === other.newerVersions

  def name: String =
    artifactId match {
      case "core" => groupId.split('.').lastOption.getOrElse(groupId)
      case _      => artifactId
    }

  def nextVersion: String =
    newerVersions.head

  def replaceAllIn(str: String): Option[String] = {
    def normalize(searchTerm: String): String =
      Update
        .removeIgnorableSuffix(searchTerm)
        .replace("-", ".?")

    val regex = s"(?i)(${normalize(name)}.*?)$currentVersion".r
    var updated = false
    val result = regex.replaceAllIn(str, m => {
      updated = true
      m.group(1) + nextVersion
    })
    if (updated) Some(result) else None
  }

  def show: String =
    s"$groupId:$artifactId : ${(currentVersion :: newerVersions).mkString_("", " -> ", "")}"
}

object Update {
  def fromString(str: String): Either[Throwable, Update] =
    Either.catchNonFatal {
      val regex = """([^\s:]+):([^\s:]+)[^\s]*\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split("->").map(_.trim).toList)
          Update(groupId, artifactId, version, newerVersions)
      }
    }

  def removeIgnorableSuffix(str: String): String =
    List("-core")
      .find(suffix => str.endsWith(suffix))
      .fold(str)(suffix => str.substring(0, str.length - suffix.length))
}
