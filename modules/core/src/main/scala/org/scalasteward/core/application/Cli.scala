/*
 * Copyright 2018-2019 scala-steward contributors
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
import cats.implicits._
import org.scalasteward.core.util.ApplicativeThrowable

trait Cli[F[_]] {
  def parseArgs(args: List[String]): F[Cli.Args]
}

object Cli {
  final case class Args(
      workspace: String,
      reposFile: String,
      gitAuthorName: String,
      gitAuthorEmail: String,
      githubApiHost: String,
      githubLogin: String,
      gitAskPass: String,
      signCommits: Boolean = false,
      whitelist: List[String] = Nil,
      readOnly: List[String] = Nil,
      disableSandbox: Boolean = false,
      doNotFork: Boolean = false
  )

  def create[F[_]](implicit F: ApplicativeThrowable[F]): Cli[F] = new Cli[F] {
    override def parseArgs(args: List[String]): F[Args] =
      F.fromEither {
        CaseApp
          .parse[Args](args)
          .bimap(e => new Throwable(e.message), { case (parsed, _) => parsed })
      }
  }
}
