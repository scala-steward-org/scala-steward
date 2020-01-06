/*
 * Copyright 2018-2020 Scala Steward contributors
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
import io.circe.Decoder
import io.circe.parser._
import org.scalasteward.core.data.{CrossDependency, Dependency, Update}
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.util.Nel

object parser {
  def parseBuildProperties(s: String): Option[SbtVersion] =
    """sbt.version\s*=\s*(.+)""".r.findFirstMatchIn(s).map(_.group(1)).map(SbtVersion.apply)

  /** Parses the output of our own `stewardDependencies` task. */
  def parseDependencies(lines: List[String]): List[Dependency] =
    lines.flatMap(line => decode[Dependency](removeSbtNoise(line)).toList)

  def parseDependenciesAndUpdates(lines: List[String]): (List[Dependency], List[Update.Single]) = {
    val updateDecoder = Decoder.instance { c =>
      for {
        dependency <- c.downField("dependency").as[Dependency]
        newerVersions <- c.downField("newerVersions").as[Nel[String]]
      } yield Update.Single(CrossDependency(dependency), newerVersions)
    }

    lines.flatMap { line =>
      parse(removeSbtNoise(line)).flatMap { json =>
        Decoder[Dependency].either(updateDecoder).decodeJson(json)
      }.toList
    }.separate
  }

  private def removeSbtNoise(s: String): String =
    s.replace("[info]", "").trim
}
