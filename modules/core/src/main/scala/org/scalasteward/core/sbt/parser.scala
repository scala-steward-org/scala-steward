/*
 * Copyright 2018-2019 scala-steward contributors
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
  def parseSingleUpdate(str: String): Either[String, Update.Single] =
    str.split("""\s:\s""") match {
      case Array(left, right) =>
        val moduleId = left.split(":").map(_.trim)
        val versions = right.split("->").map(_.trim)
        def msg(part: String) = s"failed to parse $part in '$str'"

        for {
          groupId <- Either.fromOption(moduleId.headOption, msg("groupId"))
          artifactId <- Either.fromOption(moduleId.lift(1), msg("artifactId"))
          configurations = moduleId.lift(2)
          currentVersion <- Either.fromOption(versions.headOption, msg("currentVersion"))
          newerVersionsList = versions.drop(1).toList.filterNot(_.startsWith("InvalidVersion"))
          newerVersions <- Either.fromOption(Nel.fromList(newerVersionsList), msg("newerVersions"))
        } yield Update.Single(groupId, artifactId, currentVersion, newerVersions, configurations)

      case _ => Left(s"'$str' must contain ' : ' exactly once")
    }

  def parseSingleUpdates(lines: List[String]): List[Update.Single] =
    lines
      .flatMap(line => parseSingleUpdate(removeSbtNoise(line)).toList)
      .distinct
      .sortBy(update => (update.groupId, update.artifactId, update.currentVersion))

  def removeSbtNoise(s: String): String =
    s.replace("[info]", "").trim
}
