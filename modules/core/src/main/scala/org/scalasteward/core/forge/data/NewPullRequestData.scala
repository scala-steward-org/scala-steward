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

package org.scalasteward.core.forge.data

import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAttempt
import org.scalasteward.core.edit.EditAttempt.ScalafixEdit
import org.scalasteward.core.git.{Branch, CommitMsg}
import org.scalasteward.core.nurture.{ArtifactsMetadata, UpdateInfoUrl}
import org.scalasteward.core.nurture.UpdateInfoUrl._
import org.scalasteward.core.repoconfig.{GroupRepoConfig, RepoConfigAlg}
import org.scalasteward.core.util.{Details, Nel}

final case class NewPullRequestData(
    data: UpdateData,
    head: String,
    edits: List[EditAttempt] = List.empty,
    artifactsMetadata: ArtifactsMetadata = ArtifactsMetadata.empty,
    filesWithOldVersion: List[String] = List.empty
) {
  import NewPullRequestData._

  def title: String =
    CommitMsg
      .replaceVariables(data.repoConfig.commits.messageOrDefault)(
        data.update,
        data.repoData.repo.branch
      )
      .title

  def base: Branch = data.baseBranch

  def assignees: List[String] = data.repoConfig.assignees

  def reviewers: List[String] = data.repoConfig.reviewers

  def draft: Boolean = false // never true 🤔?

  def body: String = {
    val details = List(
      migrationNote,
      oldVersionNote,
      adjustFutureUpdates.some,
      configParsingErrorDetails
    ).flatten

    val updatesText = data.update.on(
      update = u => {
        val artifacts = artifactsWithOptionalUrl(u, artifactsMetadata.artifactIdToUrl)

        val updateInfoUrls =
          artifactsMetadata.artifactIdToUpdateInfoUrls.getOrElse(u.mainArtifactId, Nil)

        s"""|Updates $artifacts ${fromTo(u)}.
            |${renderUpdateInfoUrls(updateInfoUrls).getOrElse("")}""".stripMargin.trim
      },
      grouped = g => {
        val artifacts = g.updates
          .fproduct(u => artifactsMetadata.artifactIdToUpdateInfoUrls.get(u.mainArtifactId).orEmpty)
          .map { case (u, updateInfoUrls) =>
            s"* ${artifactsWithOptionalUrl(u, artifactsMetadata.artifactIdToUrl)} ${fromTo(u)}" +
              renderUpdateInfoUrls(updateInfoUrls).map(urls => s"\n  + $urls").getOrElse("")
          }
          .mkString_("\n", "\n", "\n")

        s"""|Updates:
            |$artifacts""".stripMargin.trim
      }
    )

    val skipVersionMessage = data.update.on(
      _ => "If you'd like to skip this version, you can just close this PR. ",
      _ => ""
    )

    s"""|$updatesText
        |
        |
        |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
        |
        |${skipVersionMessage}If you have any feedback, just mention me in the comments below.
        |
        |Configure Scala Steward for your repository with a [`${RepoConfigAlg.repoConfigBasename}`](${org.scalasteward.core.BuildInfo.gitHubUrl}/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
        |
        |Have a fantastic day writing Scala!
        |
        |${details.map(_.toHtml).mkString("\n")}
        |
        |${labels.mkString("labels: ", ", ", "")}
        |""".stripMargin.trim
  }

  private[data] def oldVersionNote: Option[Details] =
    Option.when(filesWithOldVersion.nonEmpty) {
      val (number, numberWithVersion) = data.update.on(
        update = u => ("number", s"number (${u.currentVersion})"),
        grouped = _ => ("numbers", "numbers")
      )

      Details(
        s"Files still referring to the old version $number",
        s"""The following files still refer to the old version $numberWithVersion.
           |You might want to review and update them manually.
           |```
           |${filesWithOldVersion.mkString("\n")}
           |```
           |""".stripMargin.trim
      )
    }

  private[data] def adjustFutureUpdates: Details = Details(
    "Adjust future updates",
    data.update.on(
      update = u =>
        s"""|Add this to your `${RepoConfigAlg.repoConfigBasename}` file to ignore future updates of this dependency:
            |```
            |${RepoConfigAlg.configToIgnoreFurtherUpdates(u)}
            |```
            |Or, add this to slow down future updates of this dependency:
            |```
            |${GroupRepoConfig.configToSlowDownUpdatesFrequency(u)}
            |```
            |""".stripMargin.trim,
      grouped = g =>
        s"""|Add these to your `${RepoConfigAlg.repoConfigBasename}` file to ignore future updates of these dependencies:
            |```
            |${RepoConfigAlg.configToIgnoreFurtherUpdates(g)}
            |```
            |Or, add these to slow down future updates of these dependencies:
            |```
            |${GroupRepoConfig.configToSlowDownUpdatesFrequency(g)}
            |```
            |""".stripMargin.trim
    )
  )

  private def configParsingErrorDetails: Option[Details] =
    data.repoData.cache.maybeRepoConfigParsingError.map { error =>
      Details(
        s"Note that the Scala Steward config file `${RepoConfigAlg.repoConfigBasename}` wasn't parsed correctly",
        s"""|```
            |$error
            |```
            |""".stripMargin.trim
      )
    }

  private[data] def migrationNote: Option[Details] = {
    val scalafixEdits = edits.collect { case scalafixEdit: ScalafixEdit => scalafixEdit }

    Option.when(scalafixEdits.nonEmpty) {
      val body = scalafixEdits
        .map { scalafixEdit =>
          val migration = scalafixEdit.migration
          val listElements =
            (migration.rewriteRules.map(rule => s"  * $rule").toList ++ migration.doc.map(uri =>
              s"  * Documentation: $uri"
            )).mkString("\n")
          val artifactName = migration.artifactIds match {
            case Nel(one, Nil) => one
            case multiple      => multiple.toList.mkString("{", ",", "}")
          }
          val name = s"${migration.groupId.value}:$artifactName:${migration.newVersion.value}"
          val createdChange = scalafixEdit.maybeCommit.fold(" (created no change)")(_ => "")
          s"* $name$createdChange\n$listElements"
        }
        .mkString("\n")
      Details("Applied Scalafix Migrations", body)
    }
  }

  private[data] def updateTypeLabels: List[String] = {
    def forUpdate(update: Update.Single) = {
      val dependencies = update.dependencies
      if (dependencies.forall(_.configurations.contains("test")))
        "test-library-update"
      else if (dependencies.forall(_.configurations.contains("scalafix-rule")))
        "scalafix-rule-update"
      else if (dependencies.forall(_.sbtVersion.isDefined))
        "sbt-plugin-update"
      else
        "library-update"
    }

    data.update.on(u => List(forUpdate(u)), _.updates.map(forUpdate).distinct)
  }

  def labels: List[String] = {
    val commitCount = edits.flatMap(_.commits).size
    val commitCountLabel = "commit-count:" + (commitCount match {
      case n if n <= 1 => s"$n"
      case n           => s"n:$n"
    })

    def semverForUpdate(u: Update.Single): List[String] = {
      val semVerVersions =
        (SemVer.parse(u.currentVersion.value), SemVer.parse(u.nextVersion.value)).tupled
      val earlySemVerLabel = semVerVersions.flatMap { case (curr, next) =>
        SemVer.getChangeEarly(curr, next).map(c => s"early-semver-${c.render}")
      }
      val semVerSpecLabel = semVerVersions.flatMap { case (curr, next) =>
        SemVer.getChangeSpec(curr, next).map(c => s"semver-spec-${c.render}")
      }
      val versionSchemeLabel =
        artifactsMetadata.artifactIdToVersionScheme
          .get(u.mainArtifactId)
          .map(vs => s"version-scheme:$vs")
      List(earlySemVerLabel, semVerSpecLabel, versionSchemeLabel).flatten
    }
    val semverLabels =
      data.update.on(u => semverForUpdate(u), _.updates.flatMap(semverForUpdate(_)).distinct)

    val scalafixLabel = edits.collectFirst { case _: ScalafixEdit => "scalafix-migrations" }
    val oldVersionLabel = Option.when(filesWithOldVersion.nonEmpty)("old-version-remains")

    val allLabels = updateTypeLabels ++
      semverLabels ++ List(scalafixLabel, oldVersionLabel).flatten ++
      List(commitCountLabel)

    val includeMatchedLabels = data.repoData.config.pullRequests.includeMatchedLabels
    allLabels.filter(label => includeMatchedLabels.fold(true)(_.matches(label)))
  }

}

object NewPullRequestData {
  private[data] def fromTo(update: Update.Single): String =
    s"from ${update.currentVersion} to ${update.nextVersion}"

  private[data] def renderUpdateInfoUrls(updateInfoUrls: List[UpdateInfoUrl]): Option[String] =
    Option.when(updateInfoUrls.nonEmpty) {
      updateInfoUrls
        .map {
          case CustomChangelog(url)    => s"[Changelog](${url.renderString})"
          case CustomReleaseNotes(url) => s"[Release Notes](${url.renderString})"
          case GitHubReleaseNotes(url) => s"[GitHub Release Notes](${url.renderString})"
          case VersionDiff(url)        => s"[Version Diff](${url.renderString})"
        }
        .mkString(" - ")
    }

  private[data] def artifactsWithOptionalUrl(
      update: Update.Single,
      artifactIdToUrl: Map[String, Uri]
  ): String = {
    def artifactWithOptionalUrl(
        groupId: GroupId,
        artifactId: String
    ): String =
      artifactIdToUrl.get(artifactId) match {
        case Some(url) => s"[$groupId:$artifactId](${url.renderString})"
        case None      => s"$groupId:$artifactId"
      }

    update match {
      case s: Update.ForArtifactId =>
        artifactWithOptionalUrl(s.groupId, s.artifactId.name)
      case g: Update.ForGroupId =>
        g.crossDependencies
          .map(crossDependency =>
            s"* ${artifactWithOptionalUrl(g.groupId, crossDependency.head.artifactId.name)}\n"
          )
          .mkString_("\n", "", "\n")
    }
  }
}
