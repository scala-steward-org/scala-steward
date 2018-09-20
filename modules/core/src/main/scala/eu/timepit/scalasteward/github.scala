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

import _root_.io.circe.parser
import better.files.File
import cats.effect.IO
import cats.implicits._
import eu.timepit.scalasteward.gh.{AuthenticatedUser, GitHubRepo}
import eu.timepit.scalasteward.model._

object github {

  val myLogin: String =
    "scala-steward"

  val accessToken: IO[String] =
    IO((File.home / s".github/tokens/$myLogin").contentAsString.trim)

  val authenticatedUser: IO[AuthenticatedUser] =
    accessToken.map(token => AuthenticatedUser(myLogin, token))

  def createPullRequest(localUpdate: LocalUpdate): IO[List[String]] =
    accessToken.flatMap { token =>
      io.exec(
        List(
          "curl",
          "-s",
          "-X",
          "POST",
          "--header",
          "Content-Type: application/json",
          "-u",
          s"$myLogin:$token",
          "--data",
          s"""{
             |  "title": "${localUpdate.commitMsg}",
             |  "body": "${pullRequestBody(localUpdate.update)}",
             |  "head": "$myLogin:${localUpdate.updateBranch.name}",
             |  "base": "${localUpdate.localRepo.base.name}"
             |}
           """.stripMargin.trim,
          s"https://api.github.com/repos/${localUpdate.localRepo.upstream.owner}/${localUpdate.localRepo.upstream.repo}/pulls"
        ),
        File.currentWorkingDirectory
      )
    }

  def pullRequestBody(update: Update): String = {
    val artifacts = update match {
      case s: Update.Single =>
        s" ${s.groupId}:${s.artifactId} "
      case g: Update.Group =>
        g.artifactIds
          .map(artifactId => s"* ${g.groupId}:$artifactId\n")
          .mkString_("\n", "", "\n")
    }
    s"""Updates${artifacts}from ${update.currentVersion} to ${update.nextVersion}.
       |
       |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
       |
       |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention @scala-steward in the comments below.
       |
       |Have a nice day!
     """.stripMargin.trim.replace("\n", "\\n")
  }

  def createPullRequestIfNotExists(localUpdate: LocalUpdate): IO[Unit] =
    pullRequestExists(localUpdate).ifM(
      log.printInfo(s"PR ${localUpdate.updateBranch.name} already exists"),
      log.printInfo(s"Create PR ${localUpdate.updateBranch.name}") >>
        createPullRequest(localUpdate).void
    )

  def fetchUpstream(repo: GitHubRepo, dir: File): IO[Unit] = {
    val name = "upstream"
    for {
      _ <- git.exec(List("remote", "add", name, httpsUrl(repo)), dir)
      _ <- git.exec(List("fetch", name), dir)
    } yield ()
  }

  def headOf(localUpdate: LocalUpdate): String =
    s"$myLogin:${localUpdate.updateBranch.name}"

  def httpsUrl(repo: GitHubRepo): String =
    s"https://github.com/${repo.owner}/${repo.repo}.git"

  def httpsUrlWithCredentials(repo: GitHubRepo): IO[String] =
    accessToken.map(token => s"https://$myLogin:$token@github.com/${repo.owner}/${repo.repo}.git")

  def pullRequestExists(localUpdate: LocalUpdate): IO[Boolean] = {
    val repo = localUpdate.localRepo.upstream
    val path = s"https://api.github.com/repos/${repo.owner}/${repo.repo}/pulls"
    val query = s"head=${headOf(localUpdate)}&state=all"
    val url = s"$path?$query"

    for {
      token <- accessToken
      lines <- io.exec(List("curl", "-s", "-u", s"$myLogin:$token", url), localUpdate.localRepo.dir)
      json <- IO.fromEither(parser.parse(lines.mkString("\n")))
      // TODO: Option.get, are you serious?
      array <- IO(json.asArray.get)
    } yield array.nonEmpty
  }
}
