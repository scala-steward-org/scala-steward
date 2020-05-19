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

import caseapp._
import caseapp.core.Error.MalformedValue
import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import cats.implicits._
import org.http4s.Uri
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Cli._
import org.scalasteward.core.util.ApplicativeThrowable
import scala.concurrent.duration._

final class Cli[F[_]](implicit F: ApplicativeThrowable[F]) {
  def parseArgs(args: List[String]): F[Args] =
    F.fromEither {
      CaseApp.parse[Args](args).bimap(e => new Throwable(e.message), { case (parsed, _) => parsed })
    }
}

object Cli {
  final case class Args(
      workspace: String,
      reposFile: String,
      gitAuthorName: String = "Scala Steward",
      gitAuthorEmail: String,
      vcsType: SupportedVCS = SupportedVCS.GitHub,
      vcsApiHost: Uri = uri"https://api.github.com",
      vcsLogin: String,
      gitAskPass: String,
      signCommits: Boolean = false,
      whitelist: List[String] = Nil,
      readOnly: List[String] = Nil,
      disableSandbox: Boolean = false,
      doNotFork: Boolean = false,
      ignoreOptsFiles: Boolean = false,
      envVar: List[EnvVar] = Nil,
      processTimeout: FiniteDuration = 10.minutes,
      scalafixMigrations: Option[String] = None,
      groupMigrations: Option[String] = None,
      cacheTtl: FiniteDuration = 2.hours,
      cacheMissDelay: FiniteDuration = 0.milliseconds,
      bitbucketServerUseDefaultReviewers: Boolean = false
  )

  final case class EnvVar(name: String, value: String)

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

  implicit val finiteDurationArgParser: ArgParser[FiniteDuration] =
    ArgParser[String].xmapError(
      _.toString(),
      s =>
        parseFiniteDuration(s).leftMap { throwable =>
          val error = s"The value is expected in the following format: <length><unit>. ($throwable)"
          MalformedValue("FiniteDuration", error)
        }
    )

  private def parseFiniteDuration(s: String): Either[Throwable, FiniteDuration] =
    Either.catchNonFatal(Duration(s)).flatMap {
      case fd: FiniteDuration => Right(fd)
      case d                  => Left(new Throwable(s"$d is not a FiniteDuration"))
    }

  implicit val uriArgParser: ArgParser[Uri] =
    ArgParser[String].xmapError(
      _.renderString,
      s => Uri.fromString(s).leftMap(pf => MalformedValue("Uri", pf.message))
    )
}
