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

package org.scalasteward.core.update.data

import org.http4s.Uri
import org.scalasteward.core.data.{CrossDependency, Update}

sealed trait UpdateState extends Product with Serializable {
  def crossDependency: CrossDependency
}

object UpdateState {
  sealed trait WithUpdate extends UpdateState {
    def update: Update.ForArtifactId
  }

  sealed trait WithPullRequest extends WithUpdate {
    def pullRequest: Uri
  }

  final case class DependencyUpToDate(
      crossDependency: CrossDependency
  ) extends UpdateState

  final case class DependencyOutdated(
      crossDependency: CrossDependency,
      update: Update.ForArtifactId
  ) extends WithUpdate

  final case class PullRequestUpToDate(
      crossDependency: CrossDependency,
      update: Update.ForArtifactId,
      pullRequest: Uri
  ) extends WithPullRequest

  final case class PullRequestOutdated(
      crossDependency: CrossDependency,
      update: Update.ForArtifactId,
      pullRequest: Uri
  ) extends WithPullRequest

  final case class PullRequestClosed(
      crossDependency: CrossDependency,
      update: Update.ForArtifactId,
      pullRequest: Uri
  ) extends WithPullRequest

  def show(updateState: UpdateState): String = {
    val groupId = updateState.crossDependency.head.groupId
    val artifacts = updateState.crossDependency.showArtifactNames
    val version = updateState.crossDependency.head.version
    val gav = s"$groupId:$artifacts : $version"
    updateState match {
      case DependencyUpToDate(_) =>
        s"up-to-date: $gav"
      case DependencyOutdated(_, update) =>
        s"new version: $gav -> ${update.newerVersions.head}"
      case PullRequestUpToDate(_, update, pullRequest) =>
        s"PR opened: $gav -> ${update.newerVersions.head} ($pullRequest)"
      case PullRequestOutdated(_, update, pullRequest) =>
        s"PR outdated: $gav -> ${update.newerVersions.head} ($pullRequest)"
      case PullRequestClosed(_, update, pullRequest) =>
        s"PR closed: $gav -> ${update.newerVersions.head} ($pullRequest)"
    }
  }
}
