/*
 * Copyright 2018 scruffy contributors
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

package eu.timepit.scruffy

import better.files.File
import cats.effect.IO

object sbt {
  def addGlobalPlugins: IO[Unit] =
    IO {
      val plugin = """addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")"""
      (File.home / ".sbt/0.13/plugins/sbt-updates.sbt").writeText(plugin)
      (File.home / ".sbt/1.0/plugins/sbt-updates.sbt").writeText(plugin)
      ()
    }

  def dependencyUpdates(dir: File): IO[List[DependencyUpdate]] =
    io.exec(sbtCmd :+ "dependencyUpdates", dir).map(toDependencyUpdates)

  def pluginsUpdates(dir: File): IO[List[DependencyUpdate]] =
    io.exec(sbtCmd :+ ";reload plugins; dependencyUpdates", dir).map(toDependencyUpdates)

  val sbtCmd: List[String] =
    List("sbt", "-no-colors")

  def toDependencyUpdates(lines: List[String]): List[DependencyUpdate] =
    lines.flatMap { line =>
      DependencyUpdate.fromString(line.replace("[info]", "").trim).toSeq
    }
}
