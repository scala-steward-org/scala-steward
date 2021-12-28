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

package org.scalasteward.core.repoconfig

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import org.scalasteward.core.data.{ArtifactId, GroupId}
import org.scalasteward.core.edit.hooks.PostUpdateHook
import org.scalasteward.core.git.CommitMsg
import org.scalasteward.core.util.Nel

final case class PostUpdateHookConfig(
    groupId: Option[GroupId],
    artifactId: Option[String],
    command: String,
    useSandbox: Boolean,
    commitMessage: String
) {
  def toHook: PostUpdateHook =
    Nel
      .fromList(command.split(' ').toList)
      .fold(
        throw new Exception("Post update hooks must have a command defined.")
      ) { cmd =>
        PostUpdateHook(
          groupId,
          artifactId.map(ArtifactId(_)),
          command = cmd,
          useSandbox = useSandbox,
          commitMessage = _ => CommitMsg(commitMessage),
          enabledByCache = _ => true,
          enabledByConfig = _ => true
        )
      }
}

object PostUpdateHookConfig {

  implicit val postUpdateHooksConfiguration: Configuration =
    Configuration.default.withDefaults

  implicit val postUpdateHooksConfigCodec: Codec[PostUpdateHookConfig] =
    deriveConfiguredCodec

}
