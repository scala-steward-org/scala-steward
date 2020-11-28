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
import caseapp._
import caseapp.core.Error.MalformedValue
import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.syntax.literals._
import org.scalasteward.core.util.dateTime.parseFiniteDuration
import scala.concurrent.duration._

object Cli {
  final case class Args(
      workspace: File,
      reposFile: File,
      defaultRepoConf: Option[File] = None,
      gitAuthorName: String = "Scala Steward",
      gitAuthorEmail: String,
      vcsType: SupportedVCS = SupportedVCS.GitHub,
      vcsApiHost: Uri = uri"https://api.github.com",
      vcsLogin: String,
      gitAskPass: File,
      signCommits: Boolean = false,
      whitelist: List[String] = Nil,
      readOnly: List[String] = Nil,
      enableSandbox: Option[Boolean] = None,
      disableSandbox: Boolean = true,
      doNotFork: Boolean = false,
      ignoreOptsFiles: Boolean = false,
      envVar: List[EnvVar] = Nil,
      processTimeout: FiniteDuration = 10.minutes,
      scalafixMigrations: List[Uri] = Nil,
      disableDefaultScalafixMigrations: Boolean = false,
      artifactMigrations: Option[File] = None,
      cacheTtl: FiniteDuration = 2.hours,
      bitbucketServerUseDefaultReviewers: Boolean = false,
      gitlabMergeWhenPipelineSucceeds: Boolean = false,
      githubAppKeyFile: Option[File] = None,
      githubAppId: Option[Long] = None
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

  implicit val envVarArgParser: SimpleArgParser[EnvVar] =
    SimpleArgParser.from[EnvVar]("env-var") { s =>
      s.trim.split('=').toList match {
        case name :: (value @ _ :: _) =>
          Right(EnvVar(name.trim, value.mkString("=").trim))
        case _ =>
          val error = "The value is expected in the following format: NAME=VALUE."
          Left(MalformedValue("EnvVar", error))
      }
    }

  implicit val fileArgParser: ArgParser[File] =
    ArgParser[String].xmapError(
      _.toString,
      s => Either.catchNonFatal(File(s)).leftMap(t => MalformedValue("File", t.getMessage))
    )

  implicit val finiteDurationArgParser: ArgParser[FiniteDuration] =
    ArgParser[String].xmapError(
      _.toString,
      s =>
        parseFiniteDuration(s).leftMap { throwable =>
          val error = s"The value is expected in the following format: <length><unit>. ($throwable)"
          MalformedValue("FiniteDuration", error)
        }
    )

  implicit val supportedVCSArgParser: ArgParser[SupportedVCS] =
    ArgParser[String].xmapError(
      _.asString,
      s => SupportedVCS.parse(s).leftMap(error => MalformedValue("SupportedVCS", error))
    )

  implicit val uriArgParser: ArgParser[Uri] =
    ArgParser[String].xmapError(
      _.renderString,
      s => Uri.fromString(s).leftMap(pf => MalformedValue("Uri", pf.message))
    )
}
