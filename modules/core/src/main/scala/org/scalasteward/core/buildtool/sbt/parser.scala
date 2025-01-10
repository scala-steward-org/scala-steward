/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.buildtool.sbt

import cats.implicits.*
import io.circe.Decoder
import io.circe.parser.*
import org.scalasteward.core.data.*

object parser {
  private val regex = """sbt.version\s*=\s*(\S+)""".r

  def parseBuildProperties(s: String): Option[Version] =
    regex.findFirstMatchIn(s).map(_.group(1)).map(Version.apply)

  /** Parses the output of our own `stewardDependencies` task. */
  def parseDependencies(lines: List[String]): List[Scope.Dependencies] = {
    val chunks = fs2.Stream.emits(lines).map(removeSbtNoise).split(_ === "--- snip ---")
    val decoder = Decoder[Dependency].either(Decoder[Resolver])
    chunks.mapFilter { chunk =>
      val (dependencies, resolvers) = chunk.toList.flatMap(decode(_)(using decoder).toList).separate
      Option.when(dependencies.nonEmpty && resolvers.nonEmpty)(
        Scope(dependencies, resolvers.sorted)
      )
    }.toList
  }

  private def removeSbtNoise(s: String): String =
    s.replace("[info]", "").trim
}
