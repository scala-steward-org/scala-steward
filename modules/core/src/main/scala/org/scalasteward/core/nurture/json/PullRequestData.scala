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

package org.scalasteward.core.nurture.json

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.vcs.data.PullRequestState
import org.scalasteward.core.model.Update

final case class PullRequestData(
    baseSha1: Sha1,
    update: Update,
    state: PullRequestState
)

object PullRequestData {
  implicit val pullRequestDataDecoder: Decoder[PullRequestData] =
    deriveDecoder

  implicit val pullRequestDataEncoder: Encoder[PullRequestData] =
    deriveEncoder
}
