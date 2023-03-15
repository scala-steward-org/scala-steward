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

package org.scalasteward.core.repoconfig

import cats.syntax.all._
import cats.{Eq, Monoid}
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.scalasteward.core.edit.hooks.PostUpdateHook

final case class RepoConfig(
    commits: CommitsConfig = CommitsConfig(),
    pullRequests: PullRequestsConfig = PullRequestsConfig(),
    scalafmt: ScalafmtConfig = ScalafmtConfig(),
    updates: UpdatesConfig = UpdatesConfig(),
    postUpdateHooks: Option[List[PostUpdateHookConfig]] = None,
    updatePullRequests: Option[PullRequestUpdateStrategy] = None,
    buildRoots: Option[List[BuildRootConfig]] = None,
    assignees: List[String] = List.empty,
    reviewers: List[String] = List.empty,
    dependencyOverrides: List[GroupRepoConfig] = List.empty
) {
  def buildRootsOrDefault(repo: Repo): List[BuildRoot] =
    buildRoots
      .map(_.filterNot(_.relativePath.contains("..")))
      .getOrElse(List(BuildRootConfig.repoRoot))
      .map(cfg => BuildRoot(repo, cfg.relativePath))

  def postUpdateHooksOrDefault: List[PostUpdateHook] =
    postUpdateHooks.getOrElse(Nil).map(_.toHook)

  def updatePullRequestsOrDefault: PullRequestUpdateStrategy =
    updatePullRequests.getOrElse(PullRequestUpdateStrategy.default)

  def show: String =
    this.asJson.spaces2
}

object RepoConfig {
  val empty: RepoConfig = RepoConfig()

  implicit val repoConfigEq: Eq[RepoConfig] =
    Eq.fromUniversalEquals

  implicit val repoConfigConfiguration: Configuration =
    Configuration.default.withDefaults

  implicit val repoConfigCodec: Codec[RepoConfig] =
    deriveConfiguredCodec

  implicit val repoConfigMonoid: Monoid[RepoConfig] =
    Monoid.instance(
      empty,
      (x, y) =>
        () match {
          case _ if x === empty => y
          case _ if y === empty => x
          case _ =>
            RepoConfig(
              commits = x.commits |+| y.commits,
              pullRequests = x.pullRequests |+| y.pullRequests,
              scalafmt = x.scalafmt |+| y.scalafmt,
              updates = x.updates |+| y.updates,
              postUpdateHooks = x.postUpdateHooks |+| y.postUpdateHooks,
              updatePullRequests = x.updatePullRequests.orElse(y.updatePullRequests),
              buildRoots = x.buildRoots |+| y.buildRoots,
              assignees = x.assignees |+| y.assignees,
              reviewers = x.reviewers |+| y.reviewers,
              dependencyOverrides = x.dependencyOverrides |+| y.dependencyOverrides
            )
        }
    )
}
