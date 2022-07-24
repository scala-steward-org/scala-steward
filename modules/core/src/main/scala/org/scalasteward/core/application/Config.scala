/*
 * Copyright 2018-2022 Scala Steward contributors
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
import cats.Monad
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.Uri.UserInfo
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.Config._
import org.scalasteward.core.data.Resolver
import org.scalasteward.core.git.Author
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.VCSType
import org.scalasteward.core.vcs.data.AuthenticatedUser
import org.scalasteward.core.vcs.github.GitHubApp
import scala.concurrent.duration.FiniteDuration

/** Configuration for scala-steward.
  *
  * == vcsCfg.apiHost ==
  * REST API v3 endpoints prefix
  *
  * For github.com this is "https://api.github.com", see
  * [[https://developer.github.com/v3/]].
  *
  * For GitHub Enterprise this is "http(s)://[hostname]/api/v3", see
  * [[https://developer.github.com/enterprise/v3/]].
  *
  * == gitCfg.gitAskPass ==
  * Program that is invoked by scala-steward and git (via the `GIT_ASKPASS`
  * environment variable) to request the password for the user vcsCfg.vcsLogin.
  *
  * This program could just be a simple shell script that echos the password.
  *
  * See also [[https://git-scm.com/docs/gitcredentials]].
  */
final case class Config(
    workspace: File,
    reposFile: File,
    gitCfg: GitCfg,
    vcsCfg: VCSCfg,
    ignoreOptsFiles: Boolean,
    processCfg: ProcessCfg,
    repoConfigCfg: RepoConfigCfg,
    scalafixCfg: ScalafixCfg,
    artifactCfg: ArtifactCfg,
    cacheTtl: FiniteDuration,
    bitbucketServerCfg: BitbucketServerCfg,
    gitLabCfg: GitLabCfg,
    azureReposConfig: AzureReposConfig,
    githubApp: Option[GitHubApp],
    urlCheckerTestUrl: Uri,
    defaultResolver: Resolver,
    refreshBackoffPeriod: FiniteDuration
) {
  def vcsUser[F[_]](implicit
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): F[AuthenticatedUser] =
    for {
      rootDir <- workspaceAlg.rootDir
      urlWithUser = util.uri.withUserInfo
        .replace(UserInfo(vcsCfg.login, None))(vcsCfg.apiHost)
        .renderString
      prompt = s"Password for '$urlWithUser': "
      password <- processAlg.exec(Nel.of(gitCfg.gitAskPass.pathAsString, prompt), rootDir)
    } yield AuthenticatedUser(vcsCfg.login, password.mkString.trim)
}

object Config {
  final case class GitCfg(
      gitAuthor: Author,
      gitAskPass: File,
      signCommits: Boolean
  )

  final case class VCSCfg(
      tpe: VCSType,
      apiHost: Uri,
      login: String,
      doNotFork: Boolean,
      addLabels: Boolean
  )

  final case class ProcessCfg(
      envVars: List[EnvVar],
      processTimeout: FiniteDuration,
      sandboxCfg: SandboxCfg,
      maxBufferSize: Int
  )

  final case class SandboxCfg(
      whitelistedDirectories: List[String],
      readOnlyDirectories: List[String],
      enableSandbox: Boolean
  )

  final case class RepoConfigCfg(
      repoConfigs: List[Uri],
      disableDefault: Boolean
  )

  final case class ScalafixCfg(
      migrations: List[Uri],
      disableDefaults: Boolean
  )

  final case class ArtifactCfg(
      migrations: List[Uri],
      disableDefaults: Boolean
  )

  final case class BitbucketServerCfg(
      useDefaultReviewers: Boolean
  )

  final case class GitLabCfg(
      mergeWhenPipelineSucceeds: Boolean
  )

  final case class AzureReposConfig(
      organization: Option[String]
  )

}
