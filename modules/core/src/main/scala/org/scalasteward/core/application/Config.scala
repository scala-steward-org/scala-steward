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

import better.files.File
import cats.Apply
import cats.effect.Sync
import org.http4s.Uri
import org.http4s.Uri.UserInfo
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.Config.{ProcessCfg, ScalafixCfg}
import org.scalasteward.core.git.Author
import org.scalasteward.core.util
import org.scalasteward.core.vcs.data.AuthenticatedUser
import org.scalasteward.core.vcs.github.GitHubApp
import scala.concurrent.duration.FiniteDuration
import scala.sys.process.Process

/** Configuration for scala-steward.
  *
  * == [[defaultRepoConfigFile]] ==
  * Location of default repo configuration file.
  * This will be used if target repo doesn't have custom configuration.
  * Note if this file doesn't exist, empty configuration will be applied
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
    defaultRepoConfigFile: Option[File],
    gitAuthor: Author,
    vcsType: SupportedVCS,
    vcsApiHost: Uri,
    vcsLogin: String,
    gitAskPass: File,
    signCommits: Boolean,
    doNotFork: Boolean,
    ignoreOptsFiles: Boolean,
    processCfg: ProcessCfg,
    scalafixCfg: ScalafixCfg,
    artifactMigrations: Option[File],
    cacheTtl: FiniteDuration,
    bitbucketServerUseDefaultReviewers: Boolean,
    gitlabMergeWhenPipelineSucceeds: Boolean,
    githubApp: Option[GitHubApp]
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
  final case class ProcessCfg(
      envVars: List[EnvVar],
      processTimeout: FiniteDuration,
      sandboxCfg: SandboxCfg
  )

  final case class SandboxCfg(
      whitelistedDirectories: List[String],
      readOnlyDirectories: List[String],
      enableSandbox: Boolean
  )

  final case class ScalafixCfg(
      migrations: List[Uri],
      disableDefaults: Boolean
  )

  def from(args: Cli.Args): Config =
    Config(
      workspace = args.workspace,
      reposFile = args.reposFile,
      defaultRepoConfigFile = args.defaultRepoConf,
      gitAuthor = Author(args.gitAuthorName, args.gitAuthorEmail),
      vcsType = args.vcsType,
      vcsApiHost = args.vcsApiHost,
      vcsLogin = args.vcsLogin,
      gitAskPass = args.gitAskPass,
      signCommits = args.signCommits,
      doNotFork = args.doNotFork,
      ignoreOptsFiles = args.ignoreOptsFiles,
      processCfg = ProcessCfg(
        envVars = args.envVar,
        processTimeout = args.processTimeout,
        sandboxCfg = SandboxCfg(
          whitelistedDirectories = args.whitelist,
          readOnlyDirectories = args.readOnly,
          enableSandbox = args.enableSandbox.getOrElse(!args.disableSandbox)
        )
      ),
      scalafixCfg = ScalafixCfg(
        migrations = args.scalafixMigrations,
        disableDefaults = args.disableDefaultScalafixMigrations
      ),
      artifactMigrations = args.artifactMigrations,
      cacheTtl = args.cacheTtl,
      bitbucketServerUseDefaultReviewers = args.bitbucketServerUseDefaultReviewers,
      gitlabMergeWhenPipelineSucceeds = args.gitlabMergeWhenPipelineSucceeds,
      githubApp = Apply[Option].map2(args.githubAppId, args.githubAppKeyFile)(GitHubApp)
    )
}
