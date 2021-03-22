/*
 * Copyright 2018-2021 Scala Steward contributors
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

import cats.effect.BracketThrow
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.scalasteward.core.data.ProcessResult.Ignored
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.{Branch, Commit, GitAlg}

final class ApplyAlg[F[_]](implicit
    editAlg: EditAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    F: BracketThrow[F]
) {
  def applyNewUpdate(
      data: UpdateData,
      seenBranches: List[Branch],
      pushCommits: (UpdateData, List[Commit]) => F[ProcessResult],
      createPullRequest: UpdateData => F[ProcessResult]
  ): F[ProcessResult] =
    gitAlg.returnToCurrentBranch(data.repo) {
      val createBranch = logger.info(s"Create branch ${data.updateBranch.name}") >>
        gitAlg.createBranch(data.repo, data.updateBranch)
      editAlg.applyUpdate(data.repoData, data.update, createBranch).flatMap { editCommits =>
        if (editCommits.isEmpty) logger.warn("No commits created").as(Ignored)
        else
          seenBranches
            .forallM(gitAlg.diff(data.repo, _).map(_.nonEmpty))
            .ifM(
              pushCommits(data, editCommits) >> createPullRequest(data),
              logger.warn("Discovered a duplicate branch, not pushing").as[ProcessResult](Ignored)
            )
      }
    }

  def mergeAndApplyAgain(
      data: UpdateData,
      seenBranches: List[Branch],
      pushCommits: (UpdateData, List[Commit]) => F[ProcessResult]
  ): F[ProcessResult] =
    for {
      _ <- logger.info(
        s"Merge branch ${data.baseBranch.name} into ${data.updateBranch.name} and apply again"
      )
      maybeMergeCommit <- gitAlg.mergeTheirs(data.repo, data.baseBranch)
      editCommits <- editAlg.applyUpdate(data.repoData, data.update)
      result <-
        seenBranches
          .forallM(gitAlg.diff(data.repo, _).map(_.nonEmpty))
          .ifM(
            pushCommits(data, maybeMergeCommit.toList ++ editCommits),
            logger.warn("Discovered a duplicate branch, not pushing").as[ProcessResult](Ignored)
          )
    } yield result
}
