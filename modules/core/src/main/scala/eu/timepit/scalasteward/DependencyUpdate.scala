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

package eu.timepit.scruffy

import cats.data.NonEmptyList
import cats.implicits._

final case class DependencyUpdate(
    groupId: String,
    artifactId: String,
    currentVersion: String,
    newerVersions: NonEmptyList[String]
) {
  def nextVersion: String =
    newerVersions.head

  def replaceAllIn(str: String): Option[String] = {
    val regex = s"($artifactId.*?)$currentVersion".r
    var updated = false
    val result = regex.replaceAllIn(str, m => {
      updated = true
      m.group(1) + nextVersion
    })
    Some(result).filter(_ => updated)
  }
}

object DependencyUpdate {
  def fromString(str: String): Either[Throwable, DependencyUpdate] =
    Either.catchNonFatal {
      val regex = """([^\s]+):([^\s]+)\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split("->").map(_.trim).toList)
          DependencyUpdate(groupId, artifactId, version, newerVersions)
      }
    }
}
