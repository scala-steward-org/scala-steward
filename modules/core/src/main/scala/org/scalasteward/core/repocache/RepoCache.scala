/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.repocache

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.data.{Dependency, Version}
import org.scalasteward.core.git.Sha1
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.sbt.data.SbtVersion

final case class RepoCache(
    sha1: Sha1,
    dependencies: List[Dependency],
    maybeSbtVersion: Option[SbtVersion],
    maybeScalafmtVersion: Option[Version],
    maybeRepoConfig: Option[RepoConfig]
)

object RepoCache {
  implicit val repoCacheDecoder: Decoder[RepoCache] =
    deriveDecoder

  implicit val repoCacheEncoder: Encoder[RepoCache] =
    deriveEncoder
}
