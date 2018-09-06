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

object git {
  def branchName(update: DependencyUpdate): String =
    s"update/${update.artifactId}-${update.nextVersion}"

  def checkoutBranch(dir: File, branch: String): IO[List[String]] =
    exec(dir, List("checkout", branch))

  def clone(workspace: File, url: String, dir: File): IO[List[String]] =
    exec(workspace, List("clone", url, dir.pathAsString))

  def commitAll(dir: File, message: String): IO[List[String]] =
    exec(dir, List("commit", "--all", "-m", message))

  def commitMsg(update: DependencyUpdate): String =
    s"Update ${update.artifactId} to ${update.nextVersion}"

  def createBranch(dir: File, branch: String): IO[List[String]] =
    exec(dir, List("checkout", "-b", branch))

  def currentBranch(dir: File): IO[String] =
    exec(dir, List("rev-parse", "--abbrev-ref", "HEAD")).map(_.mkString.trim)

  def exec(dir: File, cmd: List[String]): IO[List[String]] =
    io.exec("git" :: cmd, dir)
}
