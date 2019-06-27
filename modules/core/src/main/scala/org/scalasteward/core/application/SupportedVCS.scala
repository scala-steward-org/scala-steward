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

import cats.Eq
import cats.implicits._
import caseapp.core.Error.MalformedValue
import caseapp.core.argparser.ArgParser

sealed trait SupportedVCS {
  import SupportedVCS.{GitHub, Gitlab}
  val asString = this match {
    case GitHub => "github"
    case Gitlab => "gitlab"
  }
}

object SupportedVCS {
  case object GitHub extends SupportedVCS
  case object Gitlab extends SupportedVCS

  implicit val supportedVCSEq: Eq[SupportedVCS] =
    Eq.fromUniversalEquals

  def parse(value: String): Either[String, SupportedVCS] = value match {
    case "github" => Right(GitHub)
    case "gitlab" => Right(Gitlab)
    case unknown  => Left(s"Unexpected string '$unknown'")
  }

  implicit val supportedVCSParser: ArgParser[SupportedVCS] =
    ArgParser[String].xmapError(
      _.asString,
      s => SupportedVCS.parse(s).leftMap(error => MalformedValue("SupportedVCS", error))
    )
}
