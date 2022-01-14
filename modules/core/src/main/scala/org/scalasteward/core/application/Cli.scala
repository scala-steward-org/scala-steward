/*
 * Copyright 2018-2021 Scala Steward contributors
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
import cats.syntax.all._
import com.monovore.decline.Opts.{flag, option, options}
import com.monovore.decline._
import org.http4s.Uri
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Config._
import org.scalasteward.core.data.Resolver
import org.scalasteward.core.git.Author
import org.scalasteward.core.util.dateTime.renderFiniteDuration
import org.scalasteward.core.vcs.VCSType
import org.scalasteward.core.vcs.VCSType.GitHub
import org.scalasteward.core.vcs.github.GitHubApp

import scala.concurrent.duration._

object Cli {
  final case class EnvVar(name: String, value: String)

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

  implicit val vcsTypeArgument: Argument[VCSType] =
    Argument.from("vcs-type") { s =>
      Validated.fromEither(VCSType.parse(s)).toValidatedNel
    }

  private val workspace: Opts[File] =
    option[File]("workspace", "Location for cache and temporary files")

  private val reposFile: Opts[File] =
    option[File]("repos-file", "A markdown formatted file with a repository list")

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
    (gitAuthor, gitAskPass, signCommits).mapN(GitCfg.apply)

  private val vcsType = {
    val help = VCSType.all.map(_.asString).mkString("One of ", ", ", "") +
      s"; default: ${GitHub.asString}"
    option[VCSType]("vcs-type", help).withDefault(GitHub)
  }

  private val vcsApiHost: Opts[Uri] =
    option[Uri]("vcs-api-host", s"API URL of the git hoster; default: ${GitHub.publicApiBaseUrl}")
      .withDefault(GitHub.publicApiBaseUrl)

  private val vcsLogin: Opts[String] =
    option[String]("vcs-login", "The user name for the git hoster")

  private val doNotFork: Opts[Boolean] =
    flag("do-not-fork", "Whether to not push the update branches to a fork; default: false").orFalse

  private val vcsCfg: Opts[VCSCfg] =
    (vcsType, vcsApiHost, vcsLogin, doNotFork).mapN(VCSCfg.apply)

  private val ignoreOptsFiles: Opts[Boolean] =
    flag(
      "ignore-opts-files",
      """Whether to remove ".jvmopts" and ".sbtopts" files before invoking the build tool"""
    ).orFalse

  private val envVar: Opts[List[EnvVar]] = {
    val help = "Assigns the value to the environment variable name (can be used multiple times)"
    options[EnvVar]("env-var", help).orEmpty
  }

  private val processTimeout: Opts[FiniteDuration] = {
    val default = 10.minutes
    val help =
      s"Timeout for external process invocations; default: ${renderFiniteDuration(default)}"
    option[FiniteDuration]("process-timeout", help).withDefault(default)
  }

  private val whitelist: Opts[List[String]] =
    options[String](
      "whitelist",
      "Directory white listed for the sandbox (can be used multiple times)"
    ).orEmpty

  private val readOnly: Opts[List[String]] =
    options[String](
      "read-only",
      "Read only directory for the sandbox (can be used multiple times)"
    ).orEmpty

  private val enableSandbox: Opts[Boolean] =
    flag("enable-sandbox", "Whether to use the sandbox")
      .map(_ => true)
      .orElse(flag("disable-sandbox", "Whether to not use the sandbox").map(_ => false))
      .orElse(Opts(false))

  private val sandboxCfg: Opts[SandboxCfg] =
    (whitelist, readOnly, enableSandbox).mapN(SandboxCfg.apply)

  private val maxBufferSize: Opts[Int] = {
    val default = 8192
    val help =
      s"Size of the buffer for the output of an external process in lines; default: $default"
    option[Int]("max-buffer-size", help).withDefault(default)
  }

  private val processCfg: Opts[ProcessCfg] =
    (envVar, processTimeout, sandboxCfg, maxBufferSize).mapN(ProcessCfg.apply)

  private val repoConfig: Opts[List[Uri]] =
    options[Uri]("repo-config", "Additional repo config file (can be used multiple times)").orEmpty

  private val disableDefaultRepoConfig: Opts[Boolean] =
    flag("disable-default-repo-config", "Whether to disable the default repo config file").orFalse

  private val repoConfigCfg: Opts[RepoConfigCfg] =
    (repoConfig, disableDefaultRepoConfig).mapN(RepoConfigCfg.apply)

  private val scalafixMigrations: Opts[List[Uri]] =
    options[Uri](
      "scalafix-migrations",
      "Additional scalafix migrations configuration file (can be used multiple times)"
    ).orEmpty

  private val disableDefaultScalafixMigrations: Opts[Boolean] =
    flag(
      "disable-default-scalafix-migrations",
      "Whether to disable the default scalafix migration file; default: false"
    ).orFalse

  private val scalafixCfg: Opts[ScalafixCfg] =
    (scalafixMigrations, disableDefaultScalafixMigrations).mapN(ScalafixCfg.apply)

  private val artifactMigrations: Opts[List[Uri]] =
    options[Uri](
      "artifact-migrations",
      "Additional artifact migration configuration file (can be used multiple times)"
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
      "Whether to assign the default reviewers to a bitbucket pull request; default: false"
    ).orFalse

  private val bitbucketServerCfg: Opts[BitbucketServerCfg] =
    bitbucketServerUseDefaultReviewers.map(BitbucketServerCfg.apply)

  private val gitlabMergeWhenPipelineSucceeds: Opts[Boolean] =
    flag(
      "gitlab-merge-when-pipeline-succeeds",
      "Whether to merge a gitlab merge request when the pipeline succeeds"
    ).orFalse

  private val gitLabCfg: Opts[GitLabCfg] =
    gitlabMergeWhenPipelineSucceeds.map(GitLabCfg.apply)

  private val githubAppId: Opts[Long] =
    option[Long]("github-app-id", "GitHub application id")

  private val githubAppKeyFile: Opts[File] =
    option[File]("github-app-key-file", "GitHub application key file")

  private val gitHubApp: Opts[Option[GitHubApp]] =
    (githubAppId, githubAppKeyFile).mapN(GitHubApp.apply).orNone

  private val refreshBackoffPeriod: Opts[FiniteDuration] = {
    val default = 0.days
    val help = "Period of time a failed build won't be triggered again" +
      s"; default: ${renderFiniteDuration(default)}"
    option[FiniteDuration]("refresh-backoff-period", help).withDefault(default)
  }

  private val urlCheckerTestUrl: Opts[Uri] = {
    val default = uri"https://github.com"
    option[Uri]("url-checker-test-url", s"default: $default").withDefault(default)
  }

  private val defaultMavenRepo: Opts[Resolver] = {
    val default = Resolver.mavenCentral
    option[String]("default-maven-repo", s"default: ${default.location}")
      .map(location => Resolver.MavenRepository("default", location, None))
      .withDefault(default)
  }

  private val configOpts: Opts[Config] = (
    workspace,
    reposFile,
    gitCfg,
    vcsCfg,
    ignoreOptsFiles,
    processCfg,
    repoConfigCfg,
    scalafixCfg,
    artifactCfg,
    cacheTtl,
    bitbucketServerCfg,
    gitLabCfg,
    gitHubApp,
    urlCheckerTestUrl,
    defaultMavenRepo,
    refreshBackoffPeriod
  ).mapN(Config.apply)

  val command: Command[Config] =
    Command("scala-steward", "")(configOpts)

  sealed trait ParseResult extends Product with Serializable
  object ParseResult {
    final case class Success(config: Config) extends ParseResult
    final case class Help(help: String) extends ParseResult
    final case class Error(error: String) extends ParseResult
  }

  def parseArgs(args: List[String]): ParseResult =
    command.parse(args) match {
      case Left(help) if help.errors.isEmpty => ParseResult.Help(help.toString)
      case Left(help)                        => ParseResult.Error(help.toString)
      case Right(config)                     => ParseResult.Success(config)
    }
}
