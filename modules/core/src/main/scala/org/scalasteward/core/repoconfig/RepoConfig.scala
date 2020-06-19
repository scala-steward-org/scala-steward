/*
 * Copyright 2018-2020 Scala Steward contributors
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

import cats.kernel.Semigroup
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import org.scalasteward.core.util.wart._

final case class RepoConfig(
    commits: CommitsConfig = CommitsConfig(),
    pullRequests: PullRequestsConfig = PullRequestsConfig(),
    updates: UpdatesConfig = UpdatesConfig(),
    updatePullRequests: Option[PullRequestUpdateStrategy] = None
) {
  def updatePullRequestsOrDefault: PullRequestUpdateStrategy =
    updatePullRequests.getOrElse(PullRequestUpdateStrategy.default)
}

object RepoConfig {
  private[repoconfig] val empty: RepoConfig = RepoConfig()

  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  implicit val repoConfigCodec: Codec[RepoConfig] =
    deriveConfiguredCodec

  implicit val semigroup: Semigroup[RepoConfig] = new Semigroup[RepoConfig] {
    override def combine(x: RepoConfig, y: RepoConfig): RepoConfig = {
      import cats.syntax.semigroup._

      //  can't use eq due to wart remover
      if (x === empty) y
      else if (y === empty) x
      else
        RepoConfig(
          commits = x.commits |+| y.commits,
          pullRequests = x.pullRequests |+| y.pullRequests,
          updates = x.updates |+| y.updates,
          updatePullRequests = x.updatePullRequests.orElse(y.updatePullRequests)
        )
    }
  }
}
