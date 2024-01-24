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

package org.scalasteward.core.application

import better.files.File
import org.http4s.Uri
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.Config._
import org.scalasteward.core.data.Resolver
import org.scalasteward.core.forge.ForgeType
import org.scalasteward.core.forge.github.GitHubApp
import org.scalasteward.core.git.Author
import org.scalasteward.core.util.Nel
import scala.concurrent.duration.FiniteDuration

/** Configuration for scala-steward.
  *
  * ==forgeCfg.apiHost==
  * REST API v3 endpoints prefix
  *
  * For github.com this is "https://api.github.com", see [[https://developer.github.com/v3/]].
  *
  * For GitHub Enterprise this is "http(s)://[hostname]/api/v3", see
  * [[https://developer.github.com/enterprise/v3/]].
  *
  * ==gitCfg.gitAskPass==
  * Program that is invoked by scala-steward and git (via the `GIT_ASKPASS` environment variable) to
  * request the password for the user forgeCfg.forgeLogin.
  *
  * This program could just be a simple shell script that echos the password.
  *
  * See also [[https://git-scm.com/docs/gitcredentials]].
  */
final case class Config(
    workspace: File,
    reposFiles: Nel[Uri],
    gitCfg: GitCfg,
    forgeCfg: ForgeCfg,
    ignoreOptsFiles: Boolean,
    processCfg: ProcessCfg,
    repoConfigCfg: RepoConfigCfg,
    scalafixCfg: ScalafixCfg,
    artifactCfg: ArtifactCfg,
    cacheTtl: FiniteDuration,
    bitbucketCfg: BitbucketCfg,
    bitbucketServerCfg: BitbucketServerCfg,
    gitLabCfg: GitLabCfg,
    azureReposCfg: AzureReposCfg,
    githubApp: Option[GitHubApp],
    urlCheckerTestUrls: Nel[Uri],
    defaultResolver: Resolver,
    refreshBackoffPeriod: FiniteDuration
) {
  def forgeSpecificCfg: ForgeSpecificCfg =
    forgeCfg.tpe match {
      case ForgeType.AzureRepos      => azureReposCfg
      case ForgeType.Bitbucket       => bitbucketCfg
      case ForgeType.BitbucketServer => bitbucketServerCfg
      case ForgeType.GitHub          => GitHubCfg()
      case ForgeType.GitLab          => gitLabCfg
      case ForgeType.Gitea           => GiteaCfg()
    }
}

object Config {
  final case class GitCfg(
      gitAuthor: Author,
      gitAskPass: File,
      signCommits: Boolean
  )

  final case class ForgeCfg(
      tpe: ForgeType,
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

  sealed trait ForgeSpecificCfg extends Product with Serializable

  final case class AzureReposCfg(
      organization: Option[String]
  ) extends ForgeSpecificCfg

  final case class BitbucketCfg(
      useDefaultReviewers: Boolean
  ) extends ForgeSpecificCfg

  final case class BitbucketServerCfg(
      useDefaultReviewers: Boolean
  ) extends ForgeSpecificCfg

  final case class GitHubCfg(
  ) extends ForgeSpecificCfg

  final case class GitLabCfg(
      mergeWhenPipelineSucceeds: Boolean,
      requiredReviewers: Option[Int],
      removeSourceBranch: Boolean
  ) extends ForgeSpecificCfg

  final case class GiteaCfg(
  ) extends ForgeSpecificCfg
}
