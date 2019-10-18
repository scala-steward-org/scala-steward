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

package org.scalasteward.core.sbt

import cats.implicits._
import io.circe.parser.decode
import org.scalasteward.core.data.{Dependency, GroupId, Update}
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.util.Nel

object parser {
  def parseBuildProperties(s: String): Option[SbtVersion] =
    """sbt.version\s*=\s*(.+)""".r.findFirstMatchIn(s).map(_.group(1)).map(SbtVersion.apply)

  /** Parses a single line of output from sbt-updates' `dependencyUpdates` task. */
  def parseSingleUpdate(line: String): Either[String, Update.Single] =
    line.split("""\s:\s""") match {
      case Array(left, right) =>
        val moduleId = left.split(":").map(_.trim)
        val versions = right.split("->").map(_.trim)
        def msg(part: String) = s"failed to parse $part in '$line'"

        for {
          groupId <- Either
            .fromOption(moduleId.headOption.filter(_.nonEmpty), msg("groupId"))
            .map(GroupId.apply)
          artifactId <- Either.fromOption(moduleId.lift(1), msg("artifactId"))
          configurations = moduleId.lift(2)
          currentVersion <- Either.fromOption(
            versions.headOption.filter(_.nonEmpty),
            msg("currentVersion")
          )
          newerVersionsList = versions
            .drop(1)
            .filterNot(v => v.startsWith("InvalidVersion") || v === currentVersion)
            .toList
          newerVersions <- Either.fromOption(Nel.fromList(newerVersionsList), msg("newerVersions"))
        } yield Update.Single(groupId, artifactId, currentVersion, newerVersions, configurations)

      case _ => Left(s"'$line' must contain ' : ' exactly once")
    }

  /** Parses the output of sbt-updates' `dependencyUpdates` task. */
  def parseSingleUpdates(lines: List[String]): List[Update.Single] =
    lines
      .flatMap(line => parseSingleUpdate(removeSbtNoise(line)).toList)
      .distinct
      .sortBy(update => (update.groupId, update.artifactId, update.currentVersion))

  /** Parses the output of our own `libraryDependenciesAsJson` task. */
  def parseDependencies(lines: List[String]): List[Dependency] =
    lines.flatMap(line => decode[List[Dependency]](removeSbtNoise(line)).getOrElse(List.empty))

  private def removeSbtNoise(s: String): String =
    s.replace("[info]", "").trim
}
