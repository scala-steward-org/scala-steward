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

import cats.syntax.all.*
import cats.{Eq, Monoid}
import io.circe.Codec
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.scalasteward.core.edit.hooks.PostUpdateHook
import org.scalasteward.core.repoconfig.RepoConfig.defaultBuildRoots

final case class RepoConfig(
    private val commits: Option[CommitsConfig] = None,
    private val pullRequests: Option[PullRequestsConfig] = None,
    private val scalafmt: Option[ScalafmtConfig] = None,
    private val updates: Option[UpdatesConfig] = None,
    private val postUpdateHooks: Option[List[PostUpdateHookConfig]] = None,
    private val updatePullRequests: Option[PullRequestUpdateStrategy] = None,
    private val buildRoots: Option[List[BuildRootConfig]] = None,
    private val assignees: Option[List[String]] = None,
    private val reviewers: Option[List[String]] = None,
    private val dependencyOverrides: Option[List[GroupRepoConfig]] = None,
    signoffCommits: Option[Boolean] = None
) {
  def commitsOrDefault: CommitsConfig =
    commits.getOrElse(CommitsConfig())

  def pullRequestsOrDefault: PullRequestsConfig =
    pullRequests.getOrElse(PullRequestsConfig())

  def scalafmtOrDefault: ScalafmtConfig =
    scalafmt.getOrElse(ScalafmtConfig())

  def updatesOrDefault: UpdatesConfig =
    updates.getOrElse(UpdatesConfig())

  def buildRootsOrDefault(repo: Repo): List[BuildRoot] =
    buildRoots
      .map(_.filterNot(_.relativePath.contains("..")))
      .getOrElse(defaultBuildRoots)
      .map(cfg => BuildRoot(repo, cfg.relativePath))

  def assigneesOrDefault: List[String] =
    assignees.getOrElse(Nil)

  def reviewersOrDefault: List[String] =
    reviewers.getOrElse(Nil)

  def dependencyOverridesOrDefault: List[GroupRepoConfig] =
    dependencyOverrides.getOrElse(Nil)

  def postUpdateHooksOrDefault: List[PostUpdateHook] =
    postUpdateHooks.getOrElse(Nil).map(_.toHook)

  def updatePullRequestsOrDefault: PullRequestUpdateStrategy =
    updatePullRequests.getOrElse(PullRequestUpdateStrategy.default)

  def show: String =
    this.asJson.deepDropNullValues.spaces2
}

object RepoConfig {
  val empty: RepoConfig = RepoConfig()

  val defaultBuildRoots: List[BuildRootConfig] =
    List(BuildRootConfig.repoRoot)

  implicit val repoConfigEq: Eq[RepoConfig] =
    Eq.fromUniversalEquals

  implicit val repoConfigCodec: Codec[RepoConfig] =
    deriveCodec

  implicit val repoConfigMonoid: Monoid[RepoConfig] =
    Monoid.instance(
      empty,
      (x, y) =>
        () match {
          case _ if x === empty => y
          case _ if y === empty => x
          case _                =>
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
              dependencyOverrides = x.dependencyOverrides |+| y.dependencyOverrides,
              signoffCommits = x.signoffCommits.orElse(y.signoffCommits)
            )
        }
    )
}
