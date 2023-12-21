/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.ghappfacade

import better.files.File
import cats.syntax.all._
import com.monovore.decline.Opts.option
import com.monovore.decline._
import org.http4s.Uri
import org.scalasteward.core.application.Cli.{fileArgument, uriArgument, workspace, ParseResult}
import org.scalasteward.core.forge.ForgeType.GitHub

object FacadeCli {
  private val githubApiHost: Opts[Uri] =
    option[Uri]("github-api-host", s"GitHub API URL; default: ${GitHub.publicApiBaseUrl}")
      .withDefault(GitHub.publicApiBaseUrl)

  private val githubAppId: Opts[Long] =
    option[Long]("github-app-id", "GitHub application id")

  private val githubAppKeyFile: Opts[File] =
    option[File]("github-app-key-file", "GitHub application key file")

  private val gitHubApp: Opts[GitHubApp] =
    (githubAppId, githubAppKeyFile).mapN(GitHubApp.apply)

  private val config: Opts[FacadeConfig] =
    (workspace, githubApiHost, gitHubApp).mapN(FacadeConfig.apply)

  private val command: Command[FacadeConfig] =
    Command("scala-steward-gh-app-facade", "")(config)

  def parseArgs(args: List[String]): ParseResult[FacadeConfig] =
    command.parse(args) match {
      case Left(help) if help.errors.isEmpty => ParseResult.Help(help.toString)
      case Left(help)                        => ParseResult.Error(help.toString)
      case Right(config)                     => ParseResult.Success(config)
    }
}
