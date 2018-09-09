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
import cats.implicits._

object git {
  def branchName(update: DependencyUpdate): String =
    s"update/${update.artifactId}-${update.nextVersion}"

  def checkoutBranch(branch: String, dir: File): IO[List[String]] =
    exec(List("checkout", branch), dir)

  def clone(url: String, dir: File, workspace: File): IO[List[String]] =
    exec(List("clone", url, dir.pathAsString), workspace)

  def commitAll(message: String, dir: File): IO[List[String]] =
    exec(
      List("commit", "--all", "-m", message, "--author=Scala steward <scala-steward@timepit.eu>"),
      dir
    )

  def commitMsg(update: DependencyUpdate): String =
    s"Update ${update.artifactId} to ${update.nextVersion}"

  def containsChanges(dir: File): IO[Boolean] =
    exec(List("status", "--porcelain"), dir).map(_.nonEmpty)

  def createBranch(branch: String, dir: File): IO[List[String]] =
    exec(List("checkout", "-b", branch), dir)

  def currentBranch(dir: File): IO[String] =
    exec(List("rev-parse", "--abbrev-ref", "HEAD"), dir).map(_.mkString.trim)

  def exec(cmd: List[String], dir: File): IO[List[String]] =
    io.exec("git" :: cmd, dir)

  def remoteBranchExists(branch: String, dir: File): IO[Boolean] =
    git.exec(List("branch", "-r"), dir).map(_.exists(_.contains(branch)))

  def returnToCurrentBranch[B](dir: File)(use: String => IO[B]): IO[B] =
    currentBranch(dir).bracket(use)(checkoutBranch(_, dir).void)
}
