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

package org.scalasteward.core.sbt

import cats.implicits._
import org.scalasteward.core.model.Update
import org.scalasteward.core.util.Nel

object parser {
  def parseSingleUpdate(str: String): Either[Throwable, Update.Single] =
    Either.catchNonFatal {
      val regex = """([^\s:]+):([^\s:]+)(:([^\s]+))?\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, _, configurationsOrNull, current, newer) =>
          val configurations = Option(configurationsOrNull)
          val newerVersions = Nel.fromListUnsafe(newer.split("->").map(_.trim).toList)
          Update.Single(groupId, artifactId, current, newerVersions, configurations)
      }
    }

  def parseSingleUpdates(lines: List[String]): List[Update.Single] =
    lines
      .flatMap { line =>
        val trimmed = line.replace("[info]", "").trim
        parseSingleUpdate(trimmed).toList
      }
      .distinct
      .sortBy(update => (update.groupId, update.artifactId, update.currentVersion))
}
