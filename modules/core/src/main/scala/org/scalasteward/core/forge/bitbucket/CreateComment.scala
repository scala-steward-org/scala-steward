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

package org.scalasteward.core.forge.bitbucket

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final private[bitbucket] case class CreateComment(content: CommentContent)
final private[bitbucket] case class CommentContent(raw: String)

private[bitbucket] object CreateComment {
  def apply(text: String): CreateComment =
    CreateComment(CommentContent(text))

  implicit val createCommentCodec: Codec[CreateComment] = deriveCodec
  implicit val commentContentCodec: Codec[CommentContent] = deriveCodec
}
