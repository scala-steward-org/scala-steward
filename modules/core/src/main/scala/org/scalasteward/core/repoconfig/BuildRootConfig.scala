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
import cats.syntax.all.*
import io.circe.{Decoder, Encoder}

final case class BuildRootConfig(relativePath: String, subProject: String)

object BuildRootConfig {
  val repoRoot: BuildRootConfig = BuildRootConfig(".", "")

  implicit val buildRootConfigDecoder: Decoder[BuildRootConfig] =
    Decoder[String].emap { str =>
      str.indexOf(':') match {
        case -1  => BuildRootConfig(str, "").asRight
        case cln =>
          val relPath = str.substring(0, cln)
          val subProj = str.substring(cln + 1, str.length)
          Either.cond(
            subProj.nonEmpty,
            BuildRootConfig(relPath, subProj),
            s"$str\nThe subproject part cannot be empty after ':'"
          )
      }
    }

  implicit val buildRootConfigEncoder: Encoder[BuildRootConfig] =
    Encoder[String].contramap {
      case brc if brc.subProject.isEmpty => brc.relativePath
      case brc                           => s"${brc.relativePath}:${brc.subProject}"
    }

  implicit val buildRootConfigEq: Eq[BuildRootConfig] =
    Eq.fromUniversalEquals
}
