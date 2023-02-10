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

package org.scalasteward.core.forge

import cats.Eq
import cats.syntax.all._
import org.http4s.syntax.literals._
import org.scalasteward.core.forge.ForgeType._
import org.scalasteward.core.util.unexpectedString

sealed trait ForgeType extends Product with Serializable {
  def publicWebHost: Option[String]
  def supportsForking: Boolean = true
  def supportsLabels: Boolean = true

  val asString: String = this match {
    case AzureRepos      => "azure-repos"
    case Bitbucket       => "bitbucket"
    case BitbucketServer => "bitbucket-server"
    case GitHub          => "github"
    case GitLab          => "gitlab"
    case Gitea           => "gitea"
  }
}

object ForgeType {
  case object AzureRepos extends ForgeType {
    override val publicWebHost: Some[String] = Some("dev.azure.com")
    override def supportsForking: Boolean = false
  }

  case object Bitbucket extends ForgeType {
    override val publicWebHost: Some[String] = Some("bitbucket.org")
    override def supportsLabels: Boolean = false
    val publicApiBaseUrl = uri"https://api.bitbucket.org/2.0"
  }

  case object BitbucketServer extends ForgeType {
    override val publicWebHost: None.type = None
    override def supportsForking: Boolean = false
    override def supportsLabels: Boolean = false
  }

  case object GitHub extends ForgeType {
    override val publicWebHost: Some[String] = Some("github.com")
    val publicApiBaseUrl = uri"https://api.github.com"
  }

  case object GitLab extends ForgeType {
    override val publicWebHost: Some[String] = Some("gitlab.com")
    val publicApiBaseUrl = uri"https://gitlab.com/api/v4"
  }

  case object Gitea extends ForgeType {
    override val publicWebHost: Option[String] = None
  }

  val all: List[ForgeType] = List(AzureRepos, Bitbucket, BitbucketServer, GitHub, GitLab, Gitea)

  def allNot(f: ForgeType => Boolean): String =
    ForgeType.all.filterNot(f).map(_.asString).mkString(", ")

  def parse(s: String): Either[String, ForgeType] =
    all.find(_.asString === s) match {
      case Some(value) => Right(value)
      case None        => unexpectedString(s, all.map(_.asString))
    }

  def fromPublicWebHost(host: String): Option[ForgeType] =
    all.find(_.publicWebHost.contains_(host))

  implicit val forgeTypeEq: Eq[ForgeType] =
    Eq.fromUniversalEquals
}
