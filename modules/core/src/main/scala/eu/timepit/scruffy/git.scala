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

import cats.effect.IO

object git {
  def branchName(update: DependencyUpdate): String =
    "update/" + update.artifactId + "-" + update.nextVersion

  def checkoutBranch(repo: Repository, branch: String): IO[List[String]] =
    exec(repo, List("checkout", branch))

  def commitMsg(update: DependencyUpdate): String =
    s"Update ${update.groupId}:${update.artifactId} to ${update.nextVersion}"

  def createBranch(repo: Repository, branch: String): IO[List[String]] =
    exec(repo, List("checkout", "-b", branch))

  def currentBranch(repo: Repository): IO[String] =
    exec(repo, List("rev-parse", "--abbrev-ref", "HEAD")).map(_.mkString.trim)

  def exec(repo: Repository, cmd: List[String]): IO[List[String]] =
    io.exec("git" :: cmd, repo.root)
}
