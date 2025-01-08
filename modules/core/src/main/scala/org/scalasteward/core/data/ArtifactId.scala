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

package org.scalasteward.core.data

import cats.Order
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalasteward.core.util.Nel

final case class ArtifactId(name: String, maybeCrossName: Option[String] = None) {
  def crossName: String =
    maybeCrossName.getOrElse(name)

  def names: Nel[String] =
    Nel(name, maybeCrossName.toList)
}

object ArtifactId {
  def apply(name: String, crossName: String): ArtifactId =
    ArtifactId(name, Some(crossName))

  implicit val artifactIdCodec: Codec[ArtifactId] =
    deriveCodec

  implicit val artifactIdOrder: Order[ArtifactId] =
    Order.by((a: ArtifactId) => (a.name, a.maybeCrossName))
}
