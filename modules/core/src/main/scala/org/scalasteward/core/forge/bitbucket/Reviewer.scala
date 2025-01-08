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

final private[bitbucket] case class DefaultReviewers(values: List[Reviewer])
private[bitbucket] object DefaultReviewers {
  implicit val defaultReviewersCodec: Codec[DefaultReviewers] = deriveCodec
}

final private[bitbucket] case class Reviewer(uuid: String)
private[bitbucket] object Reviewer {
  implicit val reviewerCodec: Codec[Reviewer] = deriveCodec
}
