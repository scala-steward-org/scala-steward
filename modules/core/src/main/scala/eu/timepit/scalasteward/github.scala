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

object github {
  val myLogin: String =
    "scala-steward"

  val accessToken: IO[String] =
    IO((File.home / s".github/tokens/$myLogin").contentAsString.trim)

  def createPullRequest(
      repo: GithubRepo,
      update: DependencyUpdate,
      updateBranch: Branch,
      baseBranch: Branch
  ): IO[List[String]] =
    accessToken.flatMap { token =>
      io.exec(
        List(
          "curl",
          "-X",
          "POST",
          "--header",
          "Content-Type: application/json",
          "-u",
          s"$myLogin:$token",
          "--data",
          s"""{
             |  "title": "${git.commitMsg(update)}",
             |  "body": "This tries to update ${update.groupId}:${update.artifactId} from ${update.currentVersion} to ${update.nextVersion}.",
             |  "head": "$myLogin:${updateBranch.name}",
             |  "base": "${baseBranch.name}"
             |}
           """.stripMargin.trim,
          s"https://api.github.com/repos/${repo.owner}/${repo.repo}/pulls"
        ),
        File.currentWorkingDirectory
      )
    }

  // ???
  def fetchUpstream(localRepo: LocalRepo): IO[Unit] = {
    val name = "upstream"
    val url = httpsUrl(localRepo.upstream)
    for {
      _ <- git.exec(List("remote", "add", name, url), localRepo.dir)
      _ <- git.exec(List("fetch", name), localRepo.dir)
    } yield ()
  }

  def fork(repo: GithubRepo): IO[List[String]] =
    accessToken.flatMap { token =>
      io.exec(
        List(
          "curl",
          "-X",
          "POST",
          "-u",
          s"$myLogin:$token",
          s"https://api.github.com/repos/${repo.owner}/${repo.repo}/forks"
        ),
        File.currentWorkingDirectory
      )
    }

  def httpsUrl(repo: GithubRepo): String =
    s"https://github.com/${repo.owner}/${repo.repo}.git"

  def httpsUrlWithCredentials(repo: GithubRepo): IO[String] =
    accessToken.map(token => s"https://$myLogin:$token@github.com/${repo.owner}/${repo.repo}.git")
}
