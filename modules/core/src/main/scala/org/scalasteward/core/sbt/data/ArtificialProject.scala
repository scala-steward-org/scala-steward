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

package org.scalasteward.core.sbt.data

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.io.FileData
import org.scalasteward.core.sbt.command.{dependencyUpdates, reloadPlugins}
import org.scalasteward.core.util
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

  def halve: Option[(ArtificialProject, ArtificialProject)] = {
    val ls = util.halve(libraries).getOrElse((Nil, Nil))
    val ps = util.halve(plugins).getOrElse((Nil, Nil))
    (ls, ps) match {
      case ((Nil, Nil), (Nil, Nil)) => None
      case ((l1, l2), (p1, p2)) =>
        Some((copy(libraries = l1, plugins = p1), copy(libraries = l2, plugins = p2)))
    }
  }
}
