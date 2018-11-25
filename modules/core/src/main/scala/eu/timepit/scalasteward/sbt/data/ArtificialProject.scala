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

package eu.timepit.scalasteward.sbt.data

import eu.timepit.scalasteward.dependency.Dependency
import eu.timepit.scalasteward.io.FileData
import eu.timepit.scalasteward.sbt.command.{dependencyUpdates, reloadPlugins}
import eu.timepit.scalasteward.util
import scala.collection.mutable.ListBuffer

final case class ArtificialProject(
    scalaVersion: ScalaVersion,
    sbtVersion: SbtVersion,
    libraries: List[Dependency],
    plugins: List[Dependency]
) {
  def dependencyUpdatesCmd: List[String] = {
    val lb = new ListBuffer[String]
    if (libraries.nonEmpty)
      lb.append(dependencyUpdates)
    if (plugins.nonEmpty)
      lb.append(reloadPlugins, dependencyUpdates)
    lb.toList
  }

  def mkBuildSbt: FileData =
    FileData(
      "build.sbt",
      s"""|scalaVersion := "${scalaVersion.value}"
          |libraryDependencies ++= Seq(
          |${libraries.map(_.formatAsModuleId).mkString(",\n")}
          |)
          |""".stripMargin.trim
    )

  def mkBuildProperties: FileData =
    FileData(
      "build.properties",
      s"sbt.version=${sbtVersion.value}"
    )

  def mkPluginsSbt: FileData =
    FileData(
      "plugins.sbt",
      plugins.map(p => s"addSbtPlugin(${p.formatAsModuleId})").mkString("\n")
    )

  def halve: List[ArtificialProject] =
    if (libraries.size <= 1 || plugins.size <= 1) Nil
    else {
      val halvedLibraries = util.halve(libraries)
      val halvedPlugins = util.halve(plugins)
      val zipped = halvedLibraries.zipAll(halvedPlugins, Nil, Nil)
      zipped.map {
        case (libraries1, plugins1) => copy(libraries = libraries1, plugins = plugins1)
      }
    }
}
