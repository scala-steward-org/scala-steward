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
import cats.data.Validated
import cats.effect.ExitCode
import cats.syntax.all._
import com.monovore.decline.Opts.{flag, option, options}
import com.monovore.decline._
import org.http4s.Uri
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Config._
import org.scalasteward.core.application.ExitCodePolicy.{
  SuccessIfAnyRepoSucceeds,
  SuccessOnlyIfAllReposSucceed
}
import org.scalasteward.core.data.Resolver
import org.scalasteward.core.forge.Forge
import org.scalasteward.core.forge.Forge._
import org.scalasteward.core.git.Author
import org.scalasteward.core.util.Nel
import org.scalasteward.core.util.dateTime.renderFiniteDuration
import scala.concurrent.duration._

object Cli {
  final case class EnvVar(name: String, value: String)

  object name {
    val forgeApiHost = "forge-api-host"
    val forgeLogin = "forge-login"
    val maxBufferSize = "max-buffer-size"
    val processTimeout = "process-timeout"
  }

  implicit val envVarArgument: Argument[EnvVar] =
    Argument.from("name=value") { s =>
      s.trim.split('=').toList match {
        case name :: (value @ _ :: _) =>
          Validated.valid(EnvVar(name.trim, value.mkString("=").trim))
        case _ =>
          val error = "The value is expected in the following format: NAME=VALUE."
          Validated.invalidNel(error)
      }
    }

  implicit val fileArgument: Argument[File] =
    Argument.from("file") { s =>
      Validated.catchNonFatal(File(s)).leftMap(_.getMessage).toValidatedNel
    }

  implicit val uriArgument: Argument[Uri] =
    Argument.from("uri") { s =>
      Validated.fromEither(Uri.fromString(s).leftMap(_.message)).toValidatedNel
    }

  private val multiple = "(can be used multiple times)"

  private val workspace: Opts[File] =
    option[File]("workspace", "Location for cache and temporary files")

  private val reposFiles: Opts[Nel[Uri]] =
    options[Uri]("repos-file", s"A markdown formatted file with a repository list $multiple")

  private val gitAuthorName: Opts[String] = {
    val default = "Scala Steward"
    option[String]("git-author-name", s"""Git "user.name"; default: $default""")
      .withDefault(default)
  }

  private val gitAuthorEmail: Opts[String] =
    option[String]("git-author-email", """Git "user.email"""")

  private val gitAuthorSigningKey: Opts[Option[String]] =
    option[String]("git-author-signing-key", """Git "user.signingKey"""").orNone

  private val gitAuthor: Opts[Author] =
    (gitAuthorName, gitAuthorEmail, gitAuthorSigningKey).mapN(Author.apply)

  private val gitAskPass: Opts[File] =
    option[File]("git-ask-pass", "An executable file that returns the git credentials")

  private val signCommits: Opts[Boolean] =
    flag("sign-commits", "Whether to sign commits; default: false").orFalse

  private val gitCfg: Opts[GitCfg] =
    (gitAuthor, signCommits).mapN(GitCfg.apply)

  private val vcsApiHost =
    option[Uri](
      "vcs-api-host",
      s"deprecated in favor of --${name.forgeApiHost}",
      visibility = Visibility.Partial
    ).validate(s"--vcs-api-host is deprecated; use --${name.forgeApiHost} instead")(_ => false)

  private val forgeApiHost: Opts[Uri] =
    option[Uri](name.forgeApiHost, s"API URL of the forge").orElse(vcsApiHost)

  private val vcsLogin =
    option[String](
      "vcs-login",
      s"deprecated in favor of --${name.forgeLogin}",
      visibility = Visibility.Partial
    ).validate(s"--vcs-login is deprecated; use --${name.forgeLogin} instead")(_ => false)

  private val forgeLogin: Opts[String] =
    option[String](name.forgeLogin, "The user name for the forge").orElse(vcsLogin)

  private val doNotFork: Opts[Boolean] =
    flag("do-not-fork", "Whether to not push the update branches to a fork; default: false").orFalse

  private val addPrLabels: Opts[Boolean] =
    flag(
      "add-labels",
      "Whether to add labels on pull or merge requests (if supported by the forge)"
    ).orFalse

  private val ignoreOptsFiles: Opts[Boolean] =
    flag(
      "ignore-opts-files",
      """Whether to remove ".jvmopts" and ".sbtopts" files before invoking the build tool"""
    ).orFalse

  private val envVar: Opts[List[EnvVar]] = {
    val help = s"Assigns the value to the environment variable name $multiple"
    options[EnvVar]("env-var", help).orEmpty
  }

  private val processTimeout: Opts[FiniteDuration] = {
    val default = 10.minutes
    val help =
      s"Timeout for external process invocations; default: ${renderFiniteDuration(default)}"
    option[FiniteDuration](name.processTimeout, help).withDefault(default)
  }

  private val whitelist: Opts[List[String]] =
    options[String](
      "whitelist",
      s"Directory white listed for the sandbox $multiple"
    ).orEmpty

  private val readOnly: Opts[List[String]] =
    options[String](
      "read-only",
      s"Read only directory for the sandbox $multiple"
    ).orEmpty

  private val enableSandbox: Opts[Boolean] = {
    val enable = flag("enable-sandbox", "Whether to use the sandbox").map(_ => true)
    val disable = flag("disable-sandbox", "Whether to not use the sandbox").map(_ => false)
    enable.orElse(disable).withDefault(false)
  }

  private val sandboxCfg: Opts[SandboxCfg] =
    (whitelist, readOnly, enableSandbox).mapN(SandboxCfg.apply)

  private val maxBufferSize: Opts[Int] = {
    val default = 32768
    val help =
      s"Size of the buffer for the output of an external process in lines; default: $default"
    option[Int](name.maxBufferSize, help).withDefault(default)
  }

  private val processCfg: Opts[ProcessCfg] =
    (envVar, processTimeout, sandboxCfg, maxBufferSize).mapN(ProcessCfg.apply)

  private val repoConfig: Opts[List[Uri]] =
    options[Uri]("repo-config", s"Additional repo config file $multiple").orEmpty

  private val disableDefaultRepoConfig: Opts[Boolean] =
    flag("disable-default-repo-config", "Whether to disable the default repo config file").orFalse

  private val repoConfigCfg: Opts[RepoConfigCfg] =
    (repoConfig, disableDefaultRepoConfig).mapN(RepoConfigCfg.apply)

  private val scalafixMigrations: Opts[List[Uri]] =
    options[Uri](
      "scalafix-migrations",
      s"Additional Scalafix migrations configuration file $multiple"
    ).orEmpty

  private val disableDefaultScalafixMigrations: Opts[Boolean] =
    flag(
      "disable-default-scalafix-migrations",
      "Whether to disable the default Scalafix migration file; default: false"
    ).orFalse

  private val scalafixCfg: Opts[ScalafixCfg] =
    (scalafixMigrations, disableDefaultScalafixMigrations).mapN(ScalafixCfg.apply)

  private val artifactMigrations: Opts[List[Uri]] =
    options[Uri](
      "artifact-migrations",
      s"Additional artifact migration configuration file $multiple"
    ).orEmpty

  private val disableDefaultArtifactMigrations: Opts[Boolean] =
    flag(
      "disable-default-artifact-migrations",
      "Whether to disable the default artifact migration file"
    ).orFalse

  private val artifactCfg: Opts[ArtifactCfg] =
    (artifactMigrations, disableDefaultArtifactMigrations).mapN(ArtifactCfg.apply)

  private val cacheTtl: Opts[FiniteDuration] = {
    val default = 2.hours
    val help = s"TTL for the caches; default: ${renderFiniteDuration(default)}"
    option[FiniteDuration]("cache-ttl", help).withDefault(default)
  }

  private val bitbucketServerUseDefaultReviewers: Opts[Boolean] =
    flag(
      "bitbucket-server-use-default-reviewers",
      "Whether to assign the default reviewers to a bitbucket server pull request; default: false"
    ).orFalse

  private val bitbucketUseDefaultReviewers: Opts[Boolean] =
    flag(
      "bitbucket-use-default-reviewers",
      "Whether to assign the default reviewers to a bitbucket pull request; default: false"
    ).orFalse

  private val gitlabMergeWhenPipelineSucceeds: Opts[Boolean] =
    flag(
      "gitlab-merge-when-pipeline-succeeds",
      "Whether to merge a gitlab merge request when the pipeline succeeds"
    ).orFalse

  private val gitlabRequiredReviewers: Opts[Option[Int]] =
    option[Int](
      "gitlab-required-reviewers",
      "When set, the number of required reviewers for a merge request will be set to this number (non-negative integer).  Is only used in the context of gitlab-merge-when-pipeline-succeeds being enabled, and requires that the configured access token have the appropriate privileges.  Also requires a Gitlab Premium subscription."
    ).validate("Required reviewers must be non-negative")(_ >= 0).orNone

  private val gitlabRemoveSourceBranch: Opts[Boolean] =
    flag(
      "gitlab-remove-source-branch",
      "Flag indicating if a merge request should remove the source branch when merging."
    ).orFalse

  private val githubAppId: Opts[Long] =
    option[Long](
      "github-app-id",
      "GitHub application id. Repos accessible by this app are added to the repos in repos.md. git-ask-pass is still required."
    )

  private val githubAppKeyFile: Opts[File] =
    option[File](
      "github-app-key-file",
      "GitHub application key file. Repos accessible by this app are added to the repos in repos.md. git-ask-pass is still required."
    )

  private val azureReposOrganization: Opts[String] =
    option[String](
      "azure-repos-organization",
      s"The Azure organization (required with --azure-repos)"
    )

  private val refreshBackoffPeriod: Opts[FiniteDuration] = {
    val default = 0.days
    val help = "Period of time a failed build won't be triggered again" +
      s"; default: ${renderFiniteDuration(default)}"
    option[FiniteDuration]("refresh-backoff-period", help).withDefault(default)
  }

  private val urlCheckerTestUrls: Opts[Nel[Uri]] = {
    val default = uri"https://github.com"
    options[Uri](
      "url-checker-test-url",
      s"URL for testing the UrlChecker at start-up $multiple; default: $default"
    ).withDefault(Nel.one(default))
  }

  private val defaultMavenRepo: Opts[Resolver] = {
    val default = Resolver.mavenCentral
    option[String]("default-maven-repo", s"default: ${default.location}")
      .map(location => Resolver.MavenRepository("default", location, None, Nil))
      .withDefault(default)
  }

  private val exitCodePolicy: Opts[ExitCodePolicy] = flag(
    "exit-code-success-if-any-repo-succeeds",
    s"Whether the Scala Steward process should exit with success (exit code ${ExitCode.Success.code}) if any repo succeeds; default: false"
  ).orFalse.map { ifAnyRepoSucceeds =>
    if (ifAnyRepoSucceeds) SuccessIfAnyRepoSucceeds else SuccessOnlyIfAllReposSucceed
  }

  private val azureRepos: Opts[Unit] =
    flag("azure-repos", "")

  private val bitbucket: Opts[Unit] =
    flag("bitbucket", "")

  private val bitbucketServer: Opts[Unit] =
    flag("bitbucket-server", "")

  private val gitLab: Opts[Unit] =
    flag("gitlab", "")

  private val gitea: Opts[Unit] =
    flag("gitea", "")

  private val gitHub: Opts[Unit] =
    flag("github", "").withDefault(()) // With default to make it succeed as default option

  private val forge: Opts[Forge] = {
    val azureReposOptions =
      (azureRepos, forgeApiHost, forgeLogin, gitAskPass, addPrLabels, azureReposOrganization).mapN(
        (_, apiUri, login, gitAskPass, addLabels, reposOrganization) =>
          AzureRepos(apiUri, login, gitAskPass, addLabels, reposOrganization)
      )
    val bitbucketOptions =
      (
        bitbucket,
        forgeApiHost.withDefault(Bitbucket.defaultApiUri),
        forgeLogin,
        gitAskPass,
        doNotFork,
        bitbucketUseDefaultReviewers
      ).mapN((_, apiUri, login, gitAskPass, doNotFork, useDefaultReviewers) =>
        Bitbucket(apiUri, login, gitAskPass, doNotFork, useDefaultReviewers)
      )
    val bitbucketServerOptions =
      (
        bitbucketServer,
        forgeApiHost,
        forgeLogin,
        gitAskPass,
        bitbucketServerUseDefaultReviewers
      ).mapN((_, apiUri, login, gitAskPass, useDefaultReviewers) =>
        BitbucketServer(apiUri, login, gitAskPass, useDefaultReviewers)
      )
    val gitLabOptions =
      (
        gitLab,
        forgeApiHost.withDefault(GitLab.defaultApiUri),
        forgeLogin,
        gitAskPass,
        doNotFork,
        addPrLabels,
        gitlabMergeWhenPipelineSucceeds,
        gitlabRequiredReviewers,
        gitlabRemoveSourceBranch
      ).mapN(
        (
            _,
            apiUri,
            login,
            gitAskPass,
            doNotFork,
            addLabels,
            mergeWhenPipelineSucceeds,
            requiredReviewers,
            removeSourceBranch
        ) =>
          GitLab(
            apiUri,
            login,
            gitAskPass,
            doNotFork,
            addLabels,
            mergeWhenPipelineSucceeds,
            requiredReviewers,
            removeSourceBranch
          )
      )
    val giteaOptions =
      (gitea, forgeApiHost, forgeLogin, gitAskPass, doNotFork, addPrLabels).mapN(
        (_, apiUri, login, gitAskPass, doNotFork, addLabels) =>
          Gitea(apiUri, login, gitAskPass, doNotFork, addLabels)
      )
    val gitHubOptions =
      (
        gitHub,
        forgeApiHost.withDefault(GitHub.defaultApiUri),
        doNotFork,
        addPrLabels,
        githubAppId,
        githubAppKeyFile
      ).mapN((_, apiUri, doNotFork, addLabels, appId, appKeyFile) =>
        GitHub(apiUri, doNotFork, addLabels, appId, appKeyFile)
      )
    azureReposOptions
      .orElse(bitbucketOptions)
      .orElse(bitbucketServerOptions)
      .orElse(gitLabOptions)
      .orElse(giteaOptions)
      .orElse(gitHubOptions) // GitHub last as default option
  }

  private val regular: Opts[Usage] = (
    workspace,
    reposFiles,
    gitCfg,
    forge,
    ignoreOptsFiles,
    processCfg,
    repoConfigCfg,
    scalafixCfg,
    artifactCfg,
    cacheTtl,
    urlCheckerTestUrls,
    defaultMavenRepo,
    refreshBackoffPeriod,
    exitCodePolicy
  ).mapN(Config.apply).map(Usage.Regular.apply)

  private val validateRepoConfig: Opts[Usage] =
    Opts
      .subcommand(
        name = "validate-repo-config",
        help = "Validate the repo config file and exit; report errors if any"
      )(Opts.argument[File]())
      .map(Usage.ValidateRepoConfig.apply)

  val command: Command[Usage] =
    Command("scala-steward", "")(regular.orElse(validateRepoConfig))

  sealed trait ParseResult extends Product with Serializable
  object ParseResult {
    final case class Success(usage: Usage) extends ParseResult
    final case class Help(help: String) extends ParseResult
    final case class Error(error: String) extends ParseResult
  }

  sealed trait Usage extends Product with Serializable
  object Usage {
    final case class Regular(config: Config) extends Usage
    final case class ValidateRepoConfig(file: File) extends Usage
  }

  def parseArgs(args: List[String]): ParseResult =
    command.parse(args) match {
      case Left(help) if help.errors.isEmpty => ParseResult.Help(help.toString)
      case Left(help)                        => ParseResult.Error(help.toString)
      case Right(usage)                      => ParseResult.Success(usage)
    }
}
