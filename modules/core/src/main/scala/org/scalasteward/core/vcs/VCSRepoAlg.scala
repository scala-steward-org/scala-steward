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

package org.scalasteward.core.vcs

import cats.MonadThrow
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.Uri.UserInfo
import org.scalasteward.core.application.Config
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.util
import org.scalasteward.core.util.logger._
import org.scalasteward.core.vcs.VCSType.GitHub
import org.scalasteward.core.vcs.data.{Repo, RepoOut}
import org.typelevel.log4cats.Logger

final class VCSRepoAlg[F[_]](config: Config)(implicit
    gitAlg: GitAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def cloneAndSync(repo: Repo, repoOut: RepoOut): F[Unit] =
    clone(repo, repoOut) >> maybeCheckoutBranchOrSyncFork(repo, repoOut) >> initSubmodules(repo)

  private def clone(repo: Repo, repoOut: RepoOut): F[Unit] =
    logger.info(s"Clone ${repoOut.repo.show}") >>
      gitAlg.clone(repo, withLogin(repoOut.clone_url)).adaptErr(adaptCloneError) >>
      gitAlg.setAuthor(repo, config.gitCfg.gitAuthor)

  private val adaptCloneError: PartialFunction[Throwable, Throwable] = {
    case throwable if config.vcsCfg.tpe === GitHub && !config.vcsCfg.doNotFork =>
      val message =
        """|If cloning failed with an error like 'access denied or repository not exported'
           |the fork might not be ready yet. This error might disappear on the next run.
           |See https://github.com/scala-steward-org/scala-steward/issues/472 for details.
           |""".stripMargin
      new Throwable(message, throwable)
  }

  private def maybeCheckoutBranchOrSyncFork(repo: Repo, repoOut: RepoOut): F[Unit] =
    if (config.vcsCfg.doNotFork) repo.branch.fold(F.unit)(gitAlg.checkoutBranch(repo, _))
    else syncFork(repo, repoOut)

  private def syncFork(repo: Repo, repoOut: RepoOut): F[Unit] =
    repoOut.parentOrRaise[F].flatMap { parent =>
      logger.info(s"Synchronize with ${parent.repo.show}") >>
        gitAlg
          .syncFork(repo, withLogin(parent.clone_url), repo.branch.getOrElse(parent.default_branch))
    }

  private def initSubmodules(repo: Repo): F[Unit] =
    logger.attemptWarn.log_("Initializing and cloning submodules failed") {
      gitAlg.initSubmodules(repo)
    }

  private val withLogin: Uri => Uri =
    util.uri.withUserInfo.replace(UserInfo(config.vcsCfg.login, None))
}
