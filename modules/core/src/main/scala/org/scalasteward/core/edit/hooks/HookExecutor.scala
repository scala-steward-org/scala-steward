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

package org.scalasteward.core.edit.hooks

import cats.MonadThrow
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.{ArtifactId, GroupId, Update}
import org.scalasteward.core.git.{Commit, GitAlg}
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.{scalafmtArtifactId, scalafmtBinary, scalafmtGroupId}
import org.scalasteward.core.util.logger._
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

final class HookExecutor[F[_]](implicit
    gitAlg: GitAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrow[F]
) {
  def execPostUpdateHooks(repo: Repo, repoConfig: RepoConfig, update: Update): F[List[Commit]] =
    HookExecutor.postUpdateHooks
      .filter { hook =>
        hook.enabledByConfig(repoConfig) &&
        update.groupId === hook.groupId &&
        update.artifactIds.exists(_.name === hook.artifactId.name)
      }
      .flatTraverse(execPostUpdateHook(repo, update, _))

  private def execPostUpdateHook(
      repo: Repo,
      update: Update,
      hook: PostUpdateHook
  ): F[List[Commit]] =
    for {
      _ <- logger.info(s"Executing post-update hook for ${hook.groupId}:${hook.artifactId.name}")
      repoDir <- workspaceAlg.repoDir(repo)
      _ <- logger.attemptLogWarn_("Post-update hook failed") {
        processAlg.execMaybeSandboxed(hook.useSandbox)(hook.command, repoDir)
      }
      maybeCommit <- gitAlg.commitAllIfDirty(repo, hook.commitMessage(update))
    } yield maybeCommit.toList
}

object HookExecutor {

  private[this] val knownGitHubActions = List(
    (GroupId("com.codecommit"), ArtifactId("sbt-github-actions")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak-sonatype")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak-bintray")),
    (GroupId("org.http4s"), ArtifactId("sbt-http4s-org"))
  )

  val postUpdateHooks: List[PostUpdateHook] = {
    val actionsActions = knownGitHubActions.map { case (gid, aid) =>
      PostUpdateHook(
        groupId = gid,
        artifactId = aid,
        command = Nel.of("sbt", "githubWorkflowGenerate"),
        useSandbox = true,
        commitMessage = _ => "Regenerate workflow with sbt-github-actions",
        enabledByConfig = _ => true
      )
    }

    actionsActions ++ List(
      PostUpdateHook(
        groupId = scalafmtGroupId,
        artifactId = scalafmtArtifactId,
        command = Nel.of(scalafmtBinary, "--non-interactive"),
        useSandbox = false,
        commitMessage = update => s"Reformat with scalafmt ${update.nextVersion}",
        enabledByConfig = _.scalafmt.runAfterUpgradingOrDefault
      )
    )
  }
}
