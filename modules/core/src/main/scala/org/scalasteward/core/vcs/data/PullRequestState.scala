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

package org.scalasteward.core.vcs.data

import cats.Eq
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.util.unexpectedString
import org.scalasteward.core.vcs.data.PullRequestState.{Closed, Open}

sealed trait PullRequestState {
  def isClosed: Boolean =
    this match {
      case Open   => false
      case Closed => true
    }
}

object PullRequestState {
  case object Open extends PullRequestState
  case object Closed extends PullRequestState

  implicit val pullRequestStateEq: Eq[PullRequestState] =
    Eq.fromUniversalEquals

  implicit val pullRequestStateDecoder: Decoder[PullRequestState] =
    Decoder[String].emap {
      _.toLowerCase match {
        case "open" | "opened"                => Right(Open)
        case "closed" | "merged" | "declined" => Right(Closed)
        case s => unexpectedString(s, List("open", "opened", "closed", "merged", "declined"))
      }
    }

  implicit val pullRequestStateEncoder: Encoder[PullRequestState] =
    Encoder[String].contramap {
      case Open   => "open"
      case Closed => "closed"
    }
}
