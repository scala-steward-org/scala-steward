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

package org.scalasteward.core.update.data

import org.http4s.Uri
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.update.FilterAlg.RejectionReason

sealed trait UpdateState extends Product with Serializable {
  def dependency: Dependency
}

object UpdateState {
  final case class DependencyUpToDate(dependency: Dependency) extends UpdateState

  final case class DependencyOutdated(dependency: Dependency, update: Update) extends UpdateState

  final case class UpdateRejectedByConfig(dependency: Dependency, rejectionReason: RejectionReason)
      extends UpdateState

  final case class PullRequestUpToDate(dependency: Dependency, update: Update, pullRequest: Uri)
      extends UpdateState

  final case class PullRequestOutdated(dependency: Dependency, update: Update, pullRequest: Uri)
      extends UpdateState

  final case class PullRequestClosed(dependency: Dependency, update: Update, pullRequest: Uri)
      extends UpdateState
}
