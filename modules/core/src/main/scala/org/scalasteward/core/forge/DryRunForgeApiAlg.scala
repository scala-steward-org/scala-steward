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

package org.scalasteward.core.forge

import cats.Monad
import cats.syntax.all.*
import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.*
import org.scalasteward.core.git.Branch
import org.typelevel.log4cats.Logger

/** A [[ForgeApiAlg]] decorator that turns all forge-mutating operations (creating forks, creating,
  * updating, closing and commenting on pull requests) into no-ops that only log what would have
  * happened. Read-only operations are delegated to `underlying`.
  *
  * This is used to implement the `--dry-run` mode.
  */
final class DryRunForgeApiAlg[F[_]](underlying: ForgeApiAlg[F])(implicit
    logger: Logger[F],
    F: Monad[F]
) extends ForgeApiAlg[F] {
  override def createFork(repo: Repo): F[RepoOut] =
    logger.info(s"[dry-run] Would create a fork of ${repo.show}") >> underlying.getRepo(repo)

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] =
    logger
      .info(s"[dry-run] Would create PR '${data.title}' in ${repo.show}")
      .as(PullRequestOut(DryRunForgeApiAlg.url, PullRequestState.Open, DryRunForgeApiAlg.number, data.title))

  override def getPullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    underlying.getPullRequest(repo, number)

  override def updatePullRequest(
      number: PullRequestNumber,
      repo: Repo,
      data: NewPullRequestData
  ): F[Unit] =
    logger.info(s"[dry-run] Would update PR #${number.value} in ${repo.show}")

  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    logger.info(s"[dry-run] Would close PR #${number.value} in ${repo.show}") >>
      underlying.getPullRequest(repo, number)

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    underlying.getBranch(repo, branch)

  override def getRepo(repo: Repo): F[RepoOut] =
    underlying.getRepo(repo)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    underlying.listPullRequests(repo, head, base)

  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] =
    logger.info(s"[dry-run] Would comment on PR #${number.value} in ${repo.show}").as(Comment(comment))
}

object DryRunForgeApiAlg {
  private val number: PullRequestNumber = PullRequestNumber(0)
  private val url: Uri = Uri.unsafeFromString("https://scala-steward.org/dry-run")
}
