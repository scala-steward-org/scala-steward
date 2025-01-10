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
import cats.data.NonEmptyList
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.data.Update

case class PullRequestGroup(
    name: String,
    title: Option[String] = None,
    filter: NonEmptyList[PullRequestUpdateFilter]
) {

  /** Returns `true` if an update falls into this group; returns `false` otherwise.
    */
  def matches(update: Update.ForArtifactId): Boolean = filter.exists(_.matches(update))

}

object PullRequestGroup {

  implicit val pullRequestGroupEq: Eq[PullRequestGroup] =
    Eq.fromUniversalEquals

  implicit val pullRequestGroupDecoder: Decoder[PullRequestGroup] =
    deriveDecoder

  implicit val pullRequestGroupEncoder: Encoder[PullRequestGroup] =
    deriveEncoder

}
