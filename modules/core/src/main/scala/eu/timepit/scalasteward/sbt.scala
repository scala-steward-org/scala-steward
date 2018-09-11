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

package eu.timepit.scalasteward

import better.files.File
import cats.effect.IO

object sbt {
  def addGlobalPlugins: IO[Unit] =
    IO {
      val pluginsDir0_13 = File.home / ".sbt/0.13/plugins"
      val pluginsDir1_0 = File.home / ".sbt/1.0/plugins"
      pluginsDir0_13.createDirectories()
      pluginsDir1_0.createDirectories()

      val plugin = """addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")"""
      (pluginsDir0_13 / "sbt-updates.sbt").writeText(plugin)
      (pluginsDir1_0 / "sbt-updates.sbt").writeText(plugin)
      ()
    }

  def allUpdates(dir: File): IO[List[DependencyUpdate]] =
    io.exec(sbtCmd :+ ";dependencyUpdates ;reload plugins; dependencyUpdates", dir)
      .map(toDependencyUpdates)

  def dependencyUpdates(dir: File): IO[List[DependencyUpdate]] =
    io.exec(sbtCmd :+ "dependencyUpdates", dir).map(toDependencyUpdates)

  def pluginsUpdates(dir: File): IO[List[DependencyUpdate]] =
    io.exec(sbtCmd :+ ";reload plugins; dependencyUpdates", dir).map(toDependencyUpdates)

  val sbtCmd: List[String] =
    List("sbt", "-no-colors")

  def toDependencyUpdates(lines: List[String]): List[DependencyUpdate] =
    lines
      .flatMap { line =>
        DependencyUpdate.fromString(line.replace("[info]", "").trim).toSeq
      }
      .distinct
      .sortBy(udate => (udate.groupId, udate.artifactId))
}
