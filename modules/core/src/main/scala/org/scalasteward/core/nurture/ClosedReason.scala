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

package org.scalasteward.core.nurture

import io.circe.{Codec, Decoder, Encoder}
import org.scalasteward.core.data.NoLongerNeeded

/** Why Scala Steward itself closed a pull request. Only reasons that influence later decisions are
  * recorded; PRs closed by humans or for other reasons have no `ClosedReason`.
  *
  * All recorded reasons mean that the base branch made the update unnecessary. Such an update may
  * be proposed again should the base branch need it again, e.g. after a revert.
  */
sealed trait ClosedReason extends Product with Serializable

object ClosedReason {

  /** The base branch already contained the proposed version or a newer one. */
  case object AlreadyApplied extends ClosedReason

  /** The base branch did not depend on the artifact anymore. */
  case object DependencyRemoved extends ClosedReason

  def of(reason: NoLongerNeeded): ClosedReason =
    reason match {
      case NoLongerNeeded.AlreadyApplied(_) => AlreadyApplied
      case NoLongerNeeded.DependencyRemoved => DependencyRemoved
    }

  private val alreadyApplied = "already-applied"
  private val dependencyRemoved = "dependency-removed"

  implicit val closedReasonCodec: Codec[ClosedReason] =
    Codec.from(
      Decoder[String].emap {
        case `alreadyApplied`    => Right(AlreadyApplied)
        case `dependencyRemoved` => Right(DependencyRemoved)
        case other               => Left(s"Unknown ClosedReason: $other")
      },
      Encoder[String].contramap {
        case AlreadyApplied    => alreadyApplied
        case DependencyRemoved => dependencyRemoved
      }
    )
}
