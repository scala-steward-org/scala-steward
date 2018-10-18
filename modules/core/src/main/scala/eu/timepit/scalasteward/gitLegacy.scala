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
import eu.timepit.scalasteward.git.Branch
import eu.timepit.scalasteward.io.ProcessAlg
import eu.timepit.scalasteward.model.Update

object gitLegacy {
  def checkoutBranch(branch: Branch, dir: File): IO[List[String]] =
    exec(List("checkout", branch.name), dir)

  def commitAll(message: String, dir: File): IO[List[String]] =
    exec(List("commit", "--all", "-m", message), dir)

  def commitMsg(update: Update): String =
    s"Update ${NameResolver.resolve(update)} to ${update.nextVersion}"

  def containsChanges(dir: File): IO[Boolean] =
    exec(List("status", "--porcelain"), dir).map(_.nonEmpty)

  def createBranch(branch: Branch, dir: File): IO[List[String]] =
    exec(List("checkout", "-b", branch.name), dir)

  def currentBranch(dir: File): IO[Branch] =
    exec(List("rev-parse", "--abbrev-ref", "HEAD"), dir).map(lines => Branch(lines.mkString.trim))

  def exec(cmd: List[String], dir: File): IO[List[String]] =
    ProcessAlg.create[IO].exec("git" :: cmd, dir)

  def remoteBranchExists(branch: Branch, dir: File): IO[Boolean] =
    gitLegacy.exec(List("branch", "-r"), dir).map(_.exists(_.endsWith(branch.name)))

  def returnToCurrentBranch[B](dir: File)(use: IO[B]): IO[B] =
    currentBranch(dir).bracket(_ => use)(checkoutBranch(_, dir).void)
}
