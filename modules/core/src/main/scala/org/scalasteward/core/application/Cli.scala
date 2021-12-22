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
import caseapp._
import caseapp.core.Error.MalformedValue
import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.util.dateTime.parseFiniteDuration
import org.scalasteward.core.vcs.VCSType
import scala.concurrent.duration._

object Cli {
  final case class Args(
      @HelpMessage("Location for cache and temporary files")
      workspace: File,
      @HelpMessage("A markdown formatted file with a repository list")
      reposFile: File,
      @HelpMessage("Git \"user.name\", default: \"Scala Steward\"")
      gitAuthorName: String = "Scala Steward",
      @HelpMessage("Email address of the git user")
      gitAuthorEmail: String,
      @HelpMessage("Git \"user.signingKey\"")
      gitAuthorSigningKey: Option[String] = None,
      @HelpMessage(
        "One of \"github\", \"gitlab\", \"bitbucket\" or \"bitbucket-server\", default: \"github\""
      )
      vcsType: VCSType = VCSType.GitHub,
      @HelpMessage("API uri of the git hoster, default: \"https://api.github.com\"")
      vcsApiHost: Uri = VCSType.GitHub.publicApiBaseUrl,
      @HelpMessage("The user name for the git hoster")
      vcsLogin: String,
      @HelpMessage("An executable file that returns the git credentials")
      gitAskPass: File,
      @HelpMessage("Whether to sign commits, default: \"false\"")
      signCommits: Boolean = false,
      @HelpMessage("Directory white listed for the sandbox (can be used multiple times)")
      whitelist: List[String] = Nil,
      @HelpMessage("Read only directory for the sandbox (can be used multiple times)")
      readOnly: List[String] = Nil,
      @HelpMessage(
        "Whether to use the sandbox, overwrites \"--disable-sandbox\", default: \"false\""
      )
      enableSandbox: Option[Boolean] = None,
      @HelpMessage("Whether to not use the sandbox, default: \"true\"")
      disableSandbox: Boolean = true,
      @HelpMessage("Whether to not push the update branches to a fork, default: \"false\"")
      doNotFork: Boolean = false,
      @HelpMessage(
        "Whether to remove \".jvmopts\" and \".sbtopts\" files before invoking the build tool"
      )
      ignoreOptsFiles: Boolean = false,
      @HelpMessage(
        "Assigns the value to the environment variable name (can be used multiple times)"
      )
      envVar: List[EnvVar] = Nil,
      @HelpMessage("Timeout for external process invocations, default: \"10min\"")
      processTimeout: FiniteDuration = 10.minutes,
      @HelpMessage(
        "Size of the buffer for the output of an external process in lines, default: \"8192\""
      )
      maxBufferSize: Int = 8192,
      @HelpMessage("Additional repo config file (can be used multiple times)")
      repoConfig: List[Uri] = Nil,
      @HelpMessage("Whether to disable the default repo config file")
      disableDefaultRepoConfig: Boolean = false,
      @HelpMessage("Additional scalafix migrations configuration file (can be used multiple times)")
      scalafixMigrations: List[Uri] = Nil,
      @HelpMessage("Whether to disable the default scalafix migration file")
      disableDefaultScalafixMigrations: Boolean = false,
      @HelpMessage("Additional artifact migration configuration file (can be used multiple times)")
      artifactMigrations: List[Uri] = Nil,
      @HelpMessage("Whether to disable the default artifact migration file")
      disableDefaultArtifactMigrations: Boolean = false,
      @HelpMessage("TTL for the caches, default: \"2hours\"")
      cacheTtl: FiniteDuration = 2.hours,
      @HelpMessage(
        "Whether to assign the default reviewers to a bitbucket pull request, default: \"false\""
      )
      bitbucketServerUseDefaultReviewers: Boolean = false,
      @HelpMessage("Whether to merge a gitlab merge request when the pipeline succeeds")
      gitlabMergeWhenPipelineSucceeds: Boolean = false,
      @HelpMessage("GitHub application key file")
      githubAppKeyFile: Option[File] = None,
      @HelpMessage("GitHub application id")
      githubAppId: Option[Long] = None,
      urlCheckerTestUrl: Option[Uri] = None,
      defaultMavenRepo: Option[String] = None,
      @HelpMessage("Period of time a failed build won't be triggered again, default: \"0days\"")
      refreshBackoffPeriod: FiniteDuration = 0.days
  )

  final case class EnvVar(name: String, value: String)

  sealed trait ParseResult extends Product with Serializable
  object ParseResult {
    final case class Success(args: Args) extends ParseResult
    final case class Help(help: String) extends ParseResult
    final case class Error(error: String) extends ParseResult
  }

  def parseArgs(args: List[String]): ParseResult = {
    val help = caseapp.core.help
      .Help[Args]
      .withAppName("Scala Steward")
      .withAppVersion(org.scalasteward.core.BuildInfo.version)
    CaseApp.parseWithHelp[Args](args) match {
      case Right((_, true, _, _))        => ParseResult.Help(help.help)
      case Right((_, _, true, _))        => ParseResult.Help(help.usage)
      case Right((Right(args), _, _, _)) => ParseResult.Success(args)
      case Right((Left(error), _, _, _)) => ParseResult.Error(error.message)
      case Left(error)                   => ParseResult.Error(error.message)
    }
  }

  implicit val envVarArgParser: ArgParser[EnvVar] =
    SimpleArgParser.from("name=value") { s =>
      s.trim.split('=').toList match {
        case name :: (value @ _ :: _) =>
          Right(EnvVar(name.trim, value.mkString("=").trim))
        case _ =>
          val error =
            "The value is expected in the following format: NAME=VALUE."
          Left(MalformedValue("EnvVar", error))
      }
    }

  implicit val fileArgParser: ArgParser[File] =
    SimpleArgParser.from("file") { s =>
      Either.catchNonFatal(File(s)).leftMap(t => MalformedValue("File", t.getMessage))
    }

  implicit val finiteDurationArgParser: ArgParser[FiniteDuration] =
    SimpleArgParser.from("duration") { s =>
      parseFiniteDuration(s).leftMap { throwable =>
        val error = s"The value is expected in the following format: <length><unit>. ($throwable)"
        MalformedValue("FiniteDuration", error)
      }
    }

  implicit val vcsTypeArgParser: ArgParser[VCSType] =
    SimpleArgParser.from("vcs-type") { s =>
      VCSType.parse(s).leftMap(error => MalformedValue("VCSType", error))
    }

  implicit val uriArgParser: ArgParser[Uri] =
    SimpleArgParser.from("uri") { s =>
      Uri.fromString(s).leftMap(pf => MalformedValue("Uri", pf.message))
    }
}
