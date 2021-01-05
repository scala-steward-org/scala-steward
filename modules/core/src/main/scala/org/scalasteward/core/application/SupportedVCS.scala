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

import cats.Eq
import cats.syntax.all._
import org.scalasteward.core.application.SupportedVCS._

sealed trait SupportedVCS {
  val asString: String = this match {
    case Bitbucket       => "bitbucket"
    case BitbucketServer => "bitbucket-server"
    case GitHub          => "github"
    case GitLab          => "gitlab"
  }
}

object SupportedVCS {
  case object Bitbucket extends SupportedVCS
  case object BitbucketServer extends SupportedVCS
  case object GitHub extends SupportedVCS
  case object GitLab extends SupportedVCS

  val all = List(Bitbucket, BitbucketServer, GitHub, GitLab)

  def parse(s: String): Either[String, SupportedVCS] =
    all.find(_.asString === s) match {
      case Some(value) => Right(value)
      case None =>
        Left(s"Unexpected string '$s'. Expected one of: ${all.map(_.asString).mkString(", ")}.")
    }

  implicit val supportedVCSEq: Eq[SupportedVCS] =
    Eq.fromUniversalEquals
}
