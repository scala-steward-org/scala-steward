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

package org.scalasteward.core.vcs

import cats.Eq
import cats.syntax.all._
import org.http4s.syntax.literals._
import org.scalasteward.core.util.unexpectedString
import org.scalasteward.core.vcs.VCSType._

sealed trait VCSType {
  def publicWebHost: Option[String]

  val asString: String = this match {
    case Bitbucket       => "bitbucket"
    case BitbucketServer => "bitbucket-server"
    case GitHub          => "github"
    case GitLab          => "gitlab"
  }
}

object VCSType {
  case object Bitbucket extends VCSType {
    override val publicWebHost: Some[String] = Some("bitbucket.org")
    val publicApiBaseUrl = uri"https://api.bitbucket.org/2.0"
  }

  case object BitbucketServer extends VCSType {
    override val publicWebHost: None.type = None
  }

  case object GitHub extends VCSType {
    override val publicWebHost: Some[String] = Some("github.com")
    val publicApiBaseUrl = uri"https://api.github.com"
  }

  case object GitLab extends VCSType {
    override val publicWebHost: Some[String] = Some("gitlab.com")
    val publicApiBaseUrl = uri"https://gitlab.com/api/v4"
  }

  val all = List(Bitbucket, BitbucketServer, GitHub, GitLab)

  def parse(s: String): Either[String, VCSType] =
    all.find(_.asString === s) match {
      case Some(value) => Right(value)
      case None        => unexpectedString(s, all.map(_.asString))
    }

  def fromPublicWebHost(host: String): Option[VCSType] =
    all.find(_.publicWebHost.contains_(host))

  implicit val vcsTypeEq: Eq[VCSType] =
    Eq.fromUniversalEquals
}
