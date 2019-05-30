/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.update

import cats.Monad
import cats.implicits._
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.model.{Label, Update}
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.update.LabelAlg.LabelsResult

class LabelAlg[F[_]](
    implicit
    repoConfigAlg: RepoConfigAlg[F],
    F: Monad[F]
) {
  def extendUpdatesWithLabels(repo: Repo, updates: List[Update.Single]): F[List[Update.Single]] =
    updates.traverse { update =>
      for {
        result <- getLabels(repo, update)
      } yield {
        result match {
          case Right(labels) => update.copy(labels = Some(labels))
          case _             => update
        }
      }
    }

  private def getLabels(repo: Repo, update: Update.Single): F[LabelsResult] =
    repoConfigAlg.getRepoConfig(repo).map { config =>
      if (config.addLabelsToPullRequests)
        Right(config.labels.getLabels(update))
      else
        Left(LabelAlg.FeatureIsDisabled)
    }
}

object LabelAlg {
  type LabelsResult = Either[FailureReason, List[Label]]

  sealed trait FailureReason
  final case object FeatureIsDisabled extends FailureReason

}
