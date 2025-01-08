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

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class DependencyInfo(
    dependency: Dependency,
    filesContainingVersion: List[String]
)

object DependencyInfo {
  implicit val dependencyInfoCodec: Codec[DependencyInfo] =
    deriveCodec
}
