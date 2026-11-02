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

import org.scalasteward.core.util.Nel

/** Why an open update PR is no longer needed, judging by the dependencies currently found in the
  * base branch of its repository.
  */
sealed trait NoLongerNeeded extends Product with Serializable

object NoLongerNeeded {

  /** Every artifact of the update is already at the proposed version or at a newer one.
    * `baseDependencies` are the matching dependencies found in the base branch, possibly several
    * versions of the same artifact.
    */
  final case class AlreadyApplied(baseDependencies: Nel[Dependency]) extends NoLongerNeeded

  /** None of the artifacts of the update is used anymore. */
  case object DependencyRemoved extends NoLongerNeeded
}
