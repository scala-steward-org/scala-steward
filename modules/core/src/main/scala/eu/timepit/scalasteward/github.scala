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
import eu.timepit.scalasteward.model._

object github {
  val myLogin: String =
    "scala-steward"

  val accessToken: IO[String] =
    IO((File.home / s".github/tokens/$myLogin").contentAsString.trim)

  def createPullRequest(localUpdate: LocalUpdate): IO[List[String]] =
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
             |  "title": "${localUpdate.commitMsg}",
             |  "body": "Update ${localUpdate.update.groupId}:${localUpdate.update.artifactId} from ${localUpdate.update.currentVersion} to ${localUpdate.update.nextVersion}.",
             |  "head": "$myLogin:${localUpdate.updateBranch.name}",
             |  "base": "${localUpdate.localRepo.base.name}"
             |}
           """.stripMargin.trim,
          s"https://api.github.com/repos/${localUpdate.localRepo.upstream.owner}/${localUpdate.localRepo.upstream.repo}/pulls"
        ),
        File.currentWorkingDirectory
      )
    }

  def fetchUpstream(repo: GithubRepo, dir: File): IO[Unit] = {
    val name = "upstream"
    for {
      _ <- git.exec(List("remote", "add", name, httpsUrl(repo)), dir)
      _ <- git.exec(List("fetch", name), dir)
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
