/*
 * Copyright 2018-2022 Scala Steward contributors
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

import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalasteward.core.data.{GroupId, Update}

final case class GroupPullRequestFrequency(
    frequency: PullRequestFrequency,
    groupId: GroupId,
    artifactId: Option[String] = None
) {
  def matches(update: Update.Single): Boolean =
    this.groupId === update.groupId && this.artifactId.forall(_ === update.mainArtifactId)
}

object GroupPullRequestFrequency {
  implicit val groupPullRequestFrequency: Codec[GroupPullRequestFrequency] =
    deriveCodec
}