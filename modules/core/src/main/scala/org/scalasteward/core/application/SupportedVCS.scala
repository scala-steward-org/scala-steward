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

import caseapp.core.Error.MalformedValue
import caseapp.core.argparser.ArgParser
import cats.Eq
import cats.implicits._

sealed trait SupportedVCS {
  import SupportedVCS.{Bitbucket, BitbucketServer, GitHub, Gitlab}
  val asString = this match {
    case GitHub          => "github"
    case Gitlab          => "gitlab"
    case Bitbucket       => "bitbucket"
    case BitbucketServer => "bitbucket-server"
  }
}

object SupportedVCS {
  case object GitHub extends SupportedVCS
  case object Gitlab extends SupportedVCS
  case object Bitbucket extends SupportedVCS
  case object BitbucketServer extends SupportedVCS

  implicit val supportedVCSEq: Eq[SupportedVCS] =
    Eq.fromUniversalEquals

  def parse(value: String): Either[String, SupportedVCS] =
    value match {
      case "github"           => Right(GitHub)
      case "gitlab"           => Right(Gitlab)
      case "bitbucket"        => Right(Bitbucket)
      case "bitbucket-server" => Right(BitbucketServer)
      case unknown            => Left(s"Unexpected string '$unknown'")
    }

  implicit val supportedVCSParser: ArgParser[SupportedVCS] =
    ArgParser[String].xmapError(
      _.asString,
      s => SupportedVCS.parse(s).leftMap(error => MalformedValue("SupportedVCS", error))
    )
}
