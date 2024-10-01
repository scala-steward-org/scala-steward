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

package org.scalasteward.core.edit.hooks

import better.files.File
import cats.MonadThrow
import cats.syntax.all._
import org.scalasteward.core.buildtool.sbt.{
  sbtArtifactId,
  sbtGroupId,
  sbtScalafixArtifactId,
  sbtScalafixGroupId
}
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAttempt
import org.scalasteward.core.edit.EditAttempt.HookEdit
import org.scalasteward.core.git.{gitBlameIgnoreRevsName, Commit, CommitMsg, GitAlg}
import org.scalasteward.core.io.process.SlurpOptions
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.scalafmt.{scalafmtArtifactId, scalafmtGroupId, ScalafmtAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.util.logger._
import org.typelevel.log4cats.Logger

final class HookExecutor[F[_]](implicit
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrow[F]
) {
  def execPostUpdateHooks(data: RepoData, update: Update.Single): F[List[EditAttempt]] =
    (HookExecutor.postUpdateHooks ++ data.config.postUpdateHooksOrDefault)
      .filter { hook =>
        hook.groupId.forall(update.groupId === _) &&
        hook.artifactId.forall(aid => update.artifactIds.exists(_.name === aid.name)) &&
        hook.enabledByCache(data.cache) &&
        hook.enabledByConfig(data.config)
      }
      .distinctBy(_.command)
      .traverse(execPostUpdateHook(data.repo, update, _))

  private def execPostUpdateHook(
      repo: Repo,
      update: Update.Single,
      hook: PostUpdateHook
  ): F[EditAttempt] =
    for {
      _ <- logger.info(
        s"Executing post-update hook for ${update.groupId}:${update.mainArtifactId} with command ${hook.showCommand}"
      )
      repoDir <- workspaceAlg.repoDir(repo)
      result <- logger.attemptWarn.log("Post-update hook failed") {
        val slurpOptions = SlurpOptions.ignoreBufferOverflow
        processAlg
          .execMaybeSandboxed(hook.useSandbox)(hook.command, repoDir, slurpOptions = slurpOptions)
          .void
      }
      commitMessage = hook
        .commitMessage(update)
        .appendParagraph(s"Executed command: ${hook.command.mkString_(" ")}")
      maybeHookCommit <- gitAlg.commitAllIfDirty(repo, commitMessage)
      maybeBlameIgnoreCommit <-
        maybeHookCommit.flatTraverse(addToGitBlameIgnoreRevs(repo, repoDir, hook, _, commitMessage))
    } yield HookEdit(hook, result, maybeHookCommit.toList ++ maybeBlameIgnoreCommit.toList)

  private def addToGitBlameIgnoreRevs(
      repo: Repo,
      repoDir: File,
      hook: PostUpdateHook,
      commit: Commit,
      commitMsg: CommitMsg
  ): F[Option[Commit]] =
    if (hook.addToGitBlameIgnoreRevs) {
      for {
        _ <- F.unit
        file = repoDir / gitBlameIgnoreRevsName
        newLines = s"# Scala Steward: ${commitMsg.title}\n${commit.sha1.value.value}\n"
        oldContent <- fileAlg.readFile(file)
        newContent = oldContent.fold(newLines)(_ + "\n" + newLines)
        _ <- fileAlg.writeFile(file, newContent)
        pathAsString = file.pathAsString

        addAndCommit = gitAlg.add(repo, pathAsString).flatMap { _ =>
          val blameIgnoreCommitMsg =
            CommitMsg(s"Add '${commitMsg.title}' to $gitBlameIgnoreRevsName")
          gitAlg.commitAllIfDirty(repo, blameIgnoreCommitMsg)
        }
        maybeBlameIgnoreCommit <- gitAlg
          .checkIgnore(repo, pathAsString)
          .ifM(
            logger
              .warn(s"Impossible to add '$pathAsString' because it is git ignored.")
              .as(Option.empty[Commit]),
            addAndCommit
          )
      } yield maybeBlameIgnoreCommit
    } else F.pure(None)
}

object HookExecutor {
  // sbt plugins that provide a githubWorkflowGenerate task.
  private val sbtGitHubWorkflowGenerateModules = List(
    (GroupId("com.codecommit"), ArtifactId("sbt-github-actions")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak-sonatype")),
    (GroupId("com.codecommit"), ArtifactId("sbt-spiewak-bintray")),
    (GroupId("com.github.sbt"), ArtifactId("sbt-github-actions")),
    (GroupId("io.chrisdavenport"), ArtifactId("sbt-davenverse")),
    (GroupId("io.github.nafg.mergify"), ArtifactId("sbt-mergify-github-actions")),
    (GroupId("org.typelevel"), ArtifactId("sbt-typelevel-ci-release")),
    (GroupId("org.typelevel"), ArtifactId("sbt-typelevel-github-actions")),
    (GroupId("org.typelevel"), ArtifactId("sbt-typelevel-mergify"))
  )

  private val sbtTypelevelModules = List(
    (GroupId("io.circe"), ArtifactId("sbt-circe-org")),
    (GroupId("org.typelevel"), ArtifactId("sbt-typelevel")),
    (GroupId("org.http4s"), ArtifactId("sbt-http4s-org")),
    (GroupId("edu.gemini"), ArtifactId("sbt-lucuma")),
    (GroupId("edu.gemini"), ArtifactId("sbt-lucuma-lib")),
    (GroupId("edu.gemini"), ArtifactId("sbt-lucuma-app"))
  )

  // Modules that most likely require the workflow to be regenerated if updated.
  private val conditionalSbtGitHubWorkflowGenerateModules =
    (sbtGroupId, sbtArtifactId) :: (sbtScalafixGroupId, sbtScalafixArtifactId) :: scalaLangModules

  private def sbtGithubWorkflowGenerateHook(
      groupId: GroupId,
      artifactId: ArtifactId,
      enabledByCache: RepoCache => Boolean
  ): PostUpdateHook =
    PostUpdateHook(
      groupId = Some(groupId),
      artifactId = Some(artifactId),
      command = Nel.of("sbt", "githubWorkflowGenerate"),
      useSandbox = true,
      commitMessage = _ => CommitMsg("Regenerate GitHub Actions workflow"),
      enabledByCache = enabledByCache,
      enabledByConfig = _ => true,
      addToGitBlameIgnoreRevs = false
    )

  private val scalafmtHook =
    PostUpdateHook(
      groupId = Some(scalafmtGroupId),
      artifactId = Some(scalafmtArtifactId),
      command = ScalafmtAlg.postUpdateHookCommand,
      useSandbox = false,
      commitMessage = update => CommitMsg(s"Reformat with scalafmt ${update.nextVersion}"),
      enabledByCache = _ => true,
      enabledByConfig = _.scalafmt.runAfterUpgradingOrDefault,
      addToGitBlameIgnoreRevs = true
    )

  private def sbtTypelevelHook(
      groupId: GroupId,
      artifactId: ArtifactId
  ): PostUpdateHook =
    PostUpdateHook(
      groupId = Some(groupId),
      artifactId = Some(artifactId),
      command = Nel.of("sbt", "tlPrePrBotHook"),
      useSandbox = true,
      commitMessage = _ => CommitMsg("Run prePR with sbt-typelevel"),
      enabledByCache = _ => true,
      enabledByConfig = _ => true,
      addToGitBlameIgnoreRevs = false
    )

  private def githubWorkflowGenerateExists(cache: RepoCache): Boolean =
    cache.dependsOn(sbtGitHubWorkflowGenerateModules ++ sbtTypelevelModules)

  private val postUpdateHooks: List[PostUpdateHook] =
    scalafmtHook ::
      sbtGitHubWorkflowGenerateModules.map { case (gid, aid) =>
        sbtGithubWorkflowGenerateHook(gid, aid, _ => true)
      } ++
      conditionalSbtGitHubWorkflowGenerateModules.map { case (gid, aid) =>
        sbtGithubWorkflowGenerateHook(gid, aid, githubWorkflowGenerateExists)
      } ++
      sbtTypelevelModules.map { case (gid, aid) =>
        sbtTypelevelHook(gid, aid)
      }
}
