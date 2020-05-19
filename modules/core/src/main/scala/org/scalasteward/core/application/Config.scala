/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.application

import better.files._
import cats.effect.Sync
import org.http4s.Uri
import org.http4s.Uri.UserInfo
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.git.Author
import org.scalasteward.core.util
import org.scalasteward.core.vcs.data.AuthenticatedUser
import scala.concurrent.duration.FiniteDuration
import scala.sys.process.Process

/** Configuration for scala-steward.
  *
  * == [[vcsApiHost]] ==
  * REST API v3 endpoints prefix
  *
  * For github.com this is "https://api.github.com", see
  * [[https://developer.github.com/v3/]].
  *
  * For GitHub Enterprise this is "http(s)://[hostname]/api/v3", see
  * [[https://developer.github.com/enterprise/v3/]].
  *
  * == [[gitAskPass]] ==
  * Program that is invoked by scala-steward and git (via the `GIT_ASKPASS`
  * environment variable) to request the password for the user [[vcsLogin]].
  *
  * This program could just be a simple shell script that echos the password.
  *
  * See also [[https://git-scm.com/docs/gitcredentials]].
  */
final case class Config(
    workspace: File,
    reposFile: File,
    gitAuthor: Author,
    vcsType: SupportedVCS,
    vcsApiHost: Uri,
    vcsLogin: String,
    gitAskPass: File,
    signCommits: Boolean,
    whitelistedDirectories: List[String],
    readOnlyDirectories: List[String],
    disableSandbox: Boolean,
    doNotFork: Boolean,
    ignoreOptsFiles: Boolean,
    envVars: List[EnvVar],
    processTimeout: FiniteDuration,
    scalafixMigrations: Option[File],
    groupMigrations: Option[File],
    cacheTtl: FiniteDuration,
    cacheMissDelay: FiniteDuration,
    bitbucketServerUseDefaultReviewers: Boolean
) {
  def vcsUser[F[_]](implicit F: Sync[F]): F[AuthenticatedUser] = {
    val urlWithUser = util.uri.withUserInfo.set(UserInfo(vcsLogin, None))(vcsApiHost).renderString
    val prompt = s"Password for '$urlWithUser': "
    F.delay {
      val password = Process(List(gitAskPass.pathAsString, prompt)).!!.trim
      AuthenticatedUser(vcsLogin, password)
    }
  }
}

object Config {
  def create[F[_]](args: Cli.Args)(implicit F: Sync[F]): F[Config] =
    F.delay {
      Config(
        workspace = args.workspace.toFile,
        reposFile = args.reposFile.toFile,
        gitAuthor = Author(args.gitAuthorName, args.gitAuthorEmail),
        vcsType = args.vcsType,
        vcsApiHost = args.vcsApiHost,
        vcsLogin = args.vcsLogin,
        gitAskPass = args.gitAskPass.toFile,
        signCommits = args.signCommits,
        whitelistedDirectories = args.whitelist,
        readOnlyDirectories = args.readOnly,
        disableSandbox = args.disableSandbox,
        doNotFork = args.doNotFork,
        ignoreOptsFiles = args.ignoreOptsFiles,
        envVars = args.envVar,
        processTimeout = args.processTimeout,
        scalafixMigrations = args.scalafixMigrations.map(_.toFile),
        groupMigrations = args.groupMigrations.map(_.toFile),
        cacheTtl = args.cacheTtl,
        cacheMissDelay = args.cacheMissDelay,
        bitbucketServerUseDefaultReviewers = args.bitbucketServerUseDefaultReviewers
      )
    }
}
