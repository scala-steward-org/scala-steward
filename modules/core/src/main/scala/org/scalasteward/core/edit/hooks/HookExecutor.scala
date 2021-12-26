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
import org.scalasteward.core.buildtool.sbt.{sbtArtifactId, sbtGroupId}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAttempt
import org.scalasteward.core.edit.EditAttempt.HookEdit
import org.scalasteward.core.git.{CommitMsg, GitAlg}
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.scalafmt.{scalafmtArtifactId, scalafmtGroupId, ScalafmtAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.util.logger._
import org.scalasteward.core.vcs.data.Repo
import org.typelevel.log4cats.Logger

final class HookExecutor[F[_]](implicit
    gitAlg: GitAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrow[F]
) {
  def execPostUpdateHooks(data: RepoData, update: Update): F[List[EditAttempt]] =
    (HookExecutor.postUpdateHooks ++ data.config.postUpdateHooks.map(_.toHook))
      .filter { hook =>
        hook.groupId.forall(update.groupId === _) &&
        hook.artifactId.forall(aid => update.artifactIds.exists(_.name === aid.name)) &&
        hook.enabledByCache(data.cache) &&
        hook.enabledByConfig(data.config)
      }
      .distinctBy(_.command)
      .traverse(execPostUpdateHook(data.repo, update, _))

  private def execPostUpdateHook(repo: Repo, update: Update, hook: PostUpdateHook): F[EditAttempt] =
    for {
      _ <- logger.info(s"Executing post-update hook for ${update.groupId}:${update.mainArtifactId}")
      repoDir <- workspaceAlg.repoDir(repo)
      result <- logger.attemptWarn.log("Post-update hook failed") {
        processAlg.execMaybeSandboxed(hook.useSandbox)(hook.command, repoDir)
      }
      maybeCommit <- gitAlg.commitAllIfDirty(repo, hook.commitMessage(update))
    } yield HookEdit(hook, result.void, maybeCommit)
}

object HookExecutor {
  // sbt plugins that depend on sbt-github-actions.
  private val sbtGitHubActionsModules = List(
    (GroupId("com.codecommit"), ArtifactId("sbt-github-actions")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak-sonatype")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak-bintray")),
    (GroupId("io.github.nafg.mergify"), ArtifactId("sbt-mergify-github-actions")),
    (GroupId("io.chrisdavenport"), ArtifactId("sbt-davenverse")),
    (GroupId("org.http4s"), ArtifactId("sbt-http4s-org"))
  )

  // Modules that most likely require the workflow to be regenerated if updated.
  private val conditionalSbtGitHubActionsModules =
    (sbtGroupId, sbtArtifactId) :: scalaLangModules

  private def sbtGithubActionsHook(
      groupId: GroupId,
      artifactId: ArtifactId,
      enabledByCache: RepoCache => Boolean
  ): PostUpdateHook =
    PostUpdateHook(
      groupId = Some(groupId),
      artifactId = Some(artifactId),
      command = Nel.of("sbt", "githubWorkflowGenerate"),
      useSandbox = true,
      commitMessage = _ => CommitMsg("Regenerate workflow with sbt-github-actions"),
      enabledByCache = enabledByCache,
      enabledByConfig = _ => true
    )

  private val scalafmtHook =
    PostUpdateHook(
      groupId = Some(scalafmtGroupId),
      artifactId = Some(scalafmtArtifactId),
      command = ScalafmtAlg.postUpdateHookCommand,
      useSandbox = false,
      commitMessage = update => CommitMsg(s"Reformat with scalafmt ${update.nextVersion}"),
      enabledByCache = _ => true,
      enabledByConfig = _.scalafmt.runAfterUpgradingOrDefault
    )

  private val sbtJavaFormatterHook =
    PostUpdateHook(
      groupId = Some(GroupId("com.lightbend.sbt")),
      artifactId = Some(ArtifactId("sbt-java-formatter")),
      command = Nel.of("sbt", "javafmtAll"),
      useSandbox = true,
      commitMessage =
        update => CommitMsg(s"Reformat with sbt-java-formatter ${update.nextVersion}"),
      enabledByCache = _ => true,
      enabledByConfig = _ => true
    )

  private val postUpdateHooks: List[PostUpdateHook] =
    scalafmtHook :: sbtJavaFormatterHook ::
      sbtGitHubActionsModules.map { case (gid, aid) =>
        sbtGithubActionsHook(gid, aid, _ => true)
      } ++
      conditionalSbtGitHubActionsModules.map { case (gid, aid) =>
        sbtGithubActionsHook(gid, aid, _.dependsOn(sbtGitHubActionsModules))
      }
}
