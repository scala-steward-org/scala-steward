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

package org.scalasteward.core.vcs.data

import cats.Eq
import io.circe.{Decoder, Encoder}

sealed trait PullRequestState

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
        case unknown                          => Left(s"Unexpected string '$unknown'")
      }
    }

  implicit val pullRequestStateEncoder: Encoder[PullRequestState] =
    Encoder[String].contramap {
      case Open   => "open"
      case Closed => "closed"
    }
}
