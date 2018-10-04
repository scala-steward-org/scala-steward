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
import eu.timepit.scalasteward.io.FileData

final case class ArtificialProject(
    scalaVersion: ScalaVersion,
    sbtVersion: SbtVersion,
    libraries: List[Dependency],
    plugins: List[Dependency]
) {
  def dependencyUpdatesCmd: String = {
    val sb = new StringBuilder
    val dependencyUpdates = ";dependencyUpdates"
    if (libraries.nonEmpty)
      sb.append(dependencyUpdates)
    if (plugins.nonEmpty) {
      sb.append(";reload plugins")
      sb.append(dependencyUpdates)
    }
    sb.result()
  }

  def mkBuildSbt: FileData =
    FileData(
      "build.sbt",
      s"""|scalaVersion := "${scalaVersion.value}"
          |${libraries.map(_.formatAsSbtExpr).mkString("\n")}
          |""".stripMargin.trim
    )

  def mkBuildProperties: FileData =
    FileData("build.properties", s"sbt.version=${sbtVersion.value}")

  def mkPluginsSbt: FileData =
    FileData("plugins.sbt", plugins.map(_.formatAsSbtExpr).mkString("\n"))
}
