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

package eu.timepit.scalasteward.sbt

import eu.timepit.scalasteward.dependency.Dependency

final case class ArtificialProject(
    scalaVersion: String,
    sbtVersion: String,
    libraries: List[Dependency],
    plugins: List[Dependency]
) {
  def mkBuildSbt: String =
    s"""|scalaVersion := "$scalaVersion"
        |${libraries.map(_.formatAsSbtExpr).mkString("\n")}
        |""".stripMargin.trim

  def mkBuildProperties: String =
    s"sbt.version=$sbtVersion"

  def mkPluginsSbt: String =
    plugins.map(_.formatAsSbtExpr).mkString("\n")
}
