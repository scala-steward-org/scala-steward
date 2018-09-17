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
import eu.timepit.scalasteward.model.Update
import cats.implicits._

object sbt {
  def addGlobalPlugins(home: File): IO[Unit] =
    IO {
      val plugin = """addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")"""
      List(
        ".sbt/0.13/plugins",
        ".sbt/1.0/plugins"
      ).foreach { path =>
        val dir = home / path
        dir.createDirectories()
        (dir / "sbt-updates.sbt").writeText(plugin)
      }
    }

  def allUpdates(dir: File): IO[List[Update]] =
    io.firejail(sbtCmd :+ ";dependencyUpdates ;reload plugins; dependencyUpdates", dir)
      .map(lines => sanitizeUpdates(toSingleUpdates(lines)))

  def sanitizeUpdates(updates: List[Update.Single]): List[Update] = {
    val distinctUpdates = updates.distinct
    distinctUpdates
      .groupByNel(single => (single.groupId, single.currentVersion, single.newerVersions))
      .mapValues(nel => if (nel.length > 1) Update.Group(nel) else nel.head)
      .values
      .toList
      .sortBy(update => (update.groupId, update.artifactId))
  }

  val sbtCmd: List[String] =
    List("sbt", "-no-colors")

  def toSingleUpdates(lines: List[String]): List[Update.Single] =
    lines.flatMap { line =>
      val trimmed = line.replace("[info]", "").trim
      Update.fromString(trimmed).toSeq
    }
}
