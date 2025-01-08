/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.repoconfig

import cats.Eq
import io.circe.{Decoder, Encoder}

sealed trait PullRequestUpdateStrategy {
  def name: String
}

object PullRequestUpdateStrategy {
  case object Always extends PullRequestUpdateStrategy { val name = "always" }
  case object OnConflicts extends PullRequestUpdateStrategy { val name = "on-conflicts" }
  case object Never extends PullRequestUpdateStrategy { val name = "never" }

  val default: PullRequestUpdateStrategy = OnConflicts

  def fromString(value: String): PullRequestUpdateStrategy =
    value.trim.toLowerCase match {
      case OnConflicts.name => OnConflicts
      case Never.name       => Never
      case Always.name      => Always
      case _                => default
    }

  def fromBoolean(value: Boolean): PullRequestUpdateStrategy =
    if (value) OnConflicts else Never

  implicit val pullRequestUpdateStrategyDecoder: Decoder[PullRequestUpdateStrategy] =
    Decoder[Boolean].map(fromBoolean).or(Decoder[String].map(fromString))

  implicit val pullRequestUpdateStrategyEncoder: Encoder[PullRequestUpdateStrategy] =
    Encoder[String].contramap(_.name)

  implicit val pullRequestUpdateStrategyEq: Eq[PullRequestUpdateStrategy] =
    Eq.fromUniversalEquals
}
