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
import eu.timepit.scalasteward.github._
import eu.timepit.scalasteward.github.data.{
  AuthenticatedUser,
  CreatePullRequestIn,
  PullRequestOut,
  Repo
}
import eu.timepit.scalasteward.model._

object githubLegacy {

  val myLogin: String =
    "scala-steward"

  val accessToken: IO[String] =
    IO((File.home / s".github/tokens/$myLogin").contentAsString.trim)

  val authenticatedUser: IO[AuthenticatedUser] =
    accessToken.map(token => AuthenticatedUser(myLogin, token))

  def createPullRequest(
      localUpdate: LocalUpdate,
      gitHubService: GitHubService[IO]
  ): IO[PullRequestOut] =
    authenticatedUser.flatMap { user =>
      val in = CreatePullRequestIn(
        title = localUpdate.commitMsg,
        body = CreatePullRequestIn.bodyOf(localUpdate.update),
        head = s"$myLogin:${localUpdate.updateBranch.name}",
        base = localUpdate.localRepo.base
      )
      gitHubService.createPullRequest(user, localUpdate.localRepo.upstream, in)
    }

  def createPullRequestIfNotExists(
      localUpdate: LocalUpdate,
      gitHubService: GitHubService[IO]
  ): IO[Unit] =
    pullRequestExists(localUpdate).ifM(
      log.printInfo(s"PR ${localUpdate.updateBranch.name} already exists"),
      log.printInfo(s"Create PR ${localUpdate.updateBranch.name}") >>
        createPullRequest(localUpdate, gitHubService).void
    )

  def fetchUpstream(cloneUrl: String, dir: File): IO[Unit] = {
    val name = "upstream"
    for {
      _ <- gitLegacy.exec(List("remote", "add", name, cloneUrl), dir)
      _ <- gitLegacy.exec(List("fetch", name), dir)
    } yield ()
  }

  def headOf(localUpdate: LocalUpdate): String =
    s"$myLogin:${localUpdate.updateBranch.name}"

  def httpsUrlWithCredentials(repo: Repo): IO[String] =
    accessToken.map(token => s"https://$myLogin:$token@github.com/${repo.owner}/${repo.repo}.git")

  def pullRequestExists(localUpdate: LocalUpdate): IO[Boolean] = {
    val repo = localUpdate.localRepo.upstream
    val path = github.url.pulls(repo)
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
