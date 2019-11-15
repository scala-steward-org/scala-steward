/*
 * Copyright 2018-2019 Scala Steward contributors
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
  /**
    * == [[projectDirs]] ==
    * If defined (it is optional) via `--project-dirs`, it specifies that scala-steward shall search for projects not
    * only in the root of a repository but based on defined
    * [[https://github.com/pathikrit/better-files#globbing glob patterns]] relative to the workspace directory.
    * Multiple glob patterns can be defined where all are used to find projects. They need to be separated with `;`.
    *
    * If you define projectDirs then you have to make sure that all repositories find a project somehow, otherwise it will
    * fail with an IllegalStateException. Or in other words, add `*&#47;*` as one pattern if you have repositories with and
    * without special project layout (i.e. build.sbt is not in the root).
    *
    * A project is identified by searching for `glob + /build.sbt` where the corresponding
    * parent directory is used as project directory.
    *
    * Following some examples:
    *
    * owner/repo1/app;**&#47;component  // use `app` as project for repository `owner/repo1` and additionally search
    *                                   // for projects located in a directory named `component` located in a repository
    *
    * owner/repo2&#47;**                // search for any project but only in a sub directory of repository `owner/repo2`
    * owner/repo2;repo2&#47;**          // search for a project located in the root of `owner/repo2` as well as
    *                                   //  any project in a sub directory of repository `owner/repo2`
    *
    * **                                // search for any project in all repositories
    * *&#47*                            // search in root of all repositories
    * *&#47*&#47*                       // search in direct sub directories of all repositories
    * *&#47*&#47**                      // search in all sub directories of all repositories
    *
    * *&#47;domain&#47;*&#47;core&#47;** // search for projects in sub directories of ./domain and ./core in any repository
    */
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
      pruneRepos: Boolean = false,
      processTimeout: FiniteDuration = 10.minutes,
      scalafixMigrations: Option[String] = None,
      projectDirs: Option[String] = None
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

  implicit val finiteDurationArgParser: ArgParser[FiniteDuration] = {
    ArgParser[String].xmapError(
      _.toString(),
      s =>
        parseFiniteDuration(s).leftMap { throwable =>
          val error = s"The value is expected in the following format: <length><unit>. ($throwable)"
          MalformedValue("FiniteDuration", error)
        }
    )
  }

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
