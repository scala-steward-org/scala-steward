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

import cats.MonadThrow
import cats.syntax.all.*
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeType.GitHub
import org.scalasteward.core.forge.data.RepoOut
import org.scalasteward.core.git.{updateBranchPrefix, Branch, GitAlg}
import org.scalasteward.core.util.logger.*
import org.typelevel.log4cats.Logger

final class ForgeRepoAlg[F[_]](config: Config)(implicit
    gitAlg: GitAlg[F],
    forgeAuthAlg: ForgeAuthAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def cloneAndSync(repo: Repo, repoOut: RepoOut): F[Unit] =
    clone(repo, repoOut) >> maybeCheckoutBranchOrSyncFork(repo, repoOut) >> initSubmodules(repo)

  private def clone(repo: Repo, repoOut: RepoOut): F[Unit] = for {
    _ <- logger.info(s"Clone ${repoOut.repo.show}")
    uri <- forgeAuthAlg.authenticateGit(repoOut.clone_url)
    _ <- gitAlg.clone(repo, uri).adaptErr(adaptCloneError)
    _ <- gitAlg.setAuthor(repo, config.gitCfg.gitAuthor)
  } yield ()

  private val adaptCloneError: PartialFunction[Throwable, Throwable] = {
    case throwable if config.forgeCfg.tpe === GitHub && !config.forgeCfg.doNotFork =>
      val message =
        """|If cloning failed with an error like 'access denied or repository not exported'
           |the fork might not be ready yet. This error might disappear on the next run.
           |See https://github.com/scala-steward-org/scala-steward/issues/472 for details.
           |""".stripMargin
      new Throwable(message, throwable)
  }

  private def maybeCheckoutBranchOrSyncFork(repo: Repo, repoOut: RepoOut): F[Unit] =
    if (config.forgeCfg.doNotFork) repo.branch.fold(F.unit)(gitAlg.checkoutBranch(repo, _))
    else syncFork(repo, repoOut)

  private def syncFork(repo: Repo, repoOut: RepoOut): F[Unit] = for {
    parent <- repoOut.parentOrRaise[F]
    _ <- logger.info(s"Synchronize with ${parent.repo.show}")
    uri <- forgeAuthAlg.authenticateGit(parent.clone_url)
    _ <- gitAlg.syncFork(repo, uri, parent.default_branch)
    _ <- deleteUpdateBranch(repo)
  } yield ()

  // We use "update" as prefix for our branches but Git doesn't allow branches named
  // "update" and "update/..." in the same repo. We therefore delete the "update" branch
  // in our fork if it exists.
  private def deleteUpdateBranch(repo: Repo): F[Unit] = {
    val local = Branch(updateBranchPrefix)
    val remote = local.withPrefix("origin/")
    gitAlg.branchExists(repo, local).ifM(gitAlg.deleteLocalBranch(repo, local), F.unit) >>
      gitAlg.branchExists(repo, remote).ifM(gitAlg.deleteRemoteBranch(repo, local), F.unit)
  }

  private def initSubmodules(repo: Repo): F[Unit] =
    logger.attemptWarn.log_("Initializing and cloning submodules failed") {
      gitAlg.initSubmodules(repo)
    }
}
