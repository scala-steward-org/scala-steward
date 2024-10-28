/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.core.forge.github

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class RepositoriesOut(repositories: List[Repository])
object RepositoriesOut {
  implicit val repositoriesCodec: Codec[RepositoriesOut] = deriveCodec
}

case class Repository(full_name: String)
object Repository {
  implicit val repositoryCodec: Codec[Repository] = deriveCodec
}
