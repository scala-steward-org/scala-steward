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
import org.scalasteward.core.nurture.UpdateInfoUrl
import org.scalasteward.core.nurture.UpdateInfoUrl._
import org.scalasteward.core.repoconfig.{GroupRepoConfig, RepoConfigAlg}
import org.scalasteward.core.util.{Details, Nel}

import scala.util.matching.Regex

final case class NewPullRequestData(
    title: String,
    body: String,
    head: String,
    base: Branch,
    labels: List[String],
    assignees: List[String],
    reviewers: List[String],
    draft: Boolean = false
)

object NewPullRequestData {
  def bodyFor(
      update: Update,
      edits: List[EditAttempt],
      artifactIdToUrl: Map[String, Uri],
      artifactIdToUpdateInfoUrls: Map[String, List[UpdateInfoUrl]],
      filesWithOldVersion: List[String],
      configParsingError: Option[String],
      labels: List[String]
  ): String = {
    val migrations = edits.collect { case scalafixEdit: ScalafixEdit => scalafixEdit }
    val appliedMigrations = migrationNote(migrations)
    val oldVersionDetails = oldVersionNote(filesWithOldVersion, update)
    val details = List(
      appliedMigrations,
      oldVersionDetails,
      adjustFutureUpdates(update).some,
      configParsingError.map(configParsingErrorDetails)
    ).flatten

    val updatesText = update.on(
      update = u => {
        val artifacts = artifactsWithOptionalUrl(u, artifactIdToUrl)

        val updateInfoUrls = artifactIdToUpdateInfoUrls.getOrElse(u.mainArtifactId, Nil)

        s"""|## About this PR
            |📦 Updates $artifacts ${fromTo(u)}${showMajorUpgradeWarning(u)}
            |${renderUpdateInfoUrls(updateInfoUrls)
             .map(urls => s"\n📜 $urls")
             .getOrElse("")}""".stripMargin.trim
      },
      grouped = g => {
        val artifacts = g.updates
          .fproduct(u => artifactIdToUpdateInfoUrls.get(u.mainArtifactId).orEmpty)
          .map { case (u, updateInfoUrls) =>
            s"* 📦 ${artifactsWithOptionalUrl(u, artifactIdToUrl)} ${fromTo(u)}${showMajorUpgradeWarning(u)}" +
              renderUpdateInfoUrls(updateInfoUrls)
                .map(urls => s"\n  + 📜 $urls")
                .getOrElse("")
          }
          .mkString_("\n", "\n", "\n")

        s"""|## About this PR
            |Updates:
            |$artifacts""".stripMargin.trim
      }
    )

    val skipVersionMessage = update.on(
      _ => "If you'd like to skip this version, you can just close this PR. ",
      _ => ""
    )

    s"""|$updatesText
        |
        |## Usage
        |✅ **Please merge!**
        |
        |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
        |
        |${skipVersionMessage}If you have any feedback, just mention me in the comments below.
        |
        |Configure Scala Steward for your repository with a [`${RepoConfigAlg.repoConfigBasename}`](${org.scalasteward.core.BuildInfo.gitHubUrl}/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
        |
        |_Have a fantastic day writing Scala!_
        |
        |${details.map(_.toHtml).mkString("\n")}
        |
        |<sup>
        |${labels.mkString("labels: ", ", ", "")}
        |</sup>
        |""".stripMargin.trim
  }

  def renderUpdateInfoUrls(updateInfoUrls: List[UpdateInfoUrl]): Option[String] =
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

  def fromTo(update: Update.Single): String =
    s"from `${update.currentVersion}` to `${update.nextVersion}`"

  def showMajorUpgradeWarning(u: Update.Single): String = {
    val semVerVersions =
      (SemVer.parse(u.currentVersion.value), SemVer.parse(u.nextVersion.value)).tupled
    val semVerLabel = semVerVersions.flatMap { case (curr, next) =>
      SemVer.getChangeSpec(curr, next).map(c => c.render)
    }
    if (semVerLabel == Some("major"))
      s" ⚠"
    else s""
  }

  def artifactsWithOptionalUrl(update: Update.Single, artifactIdToUrl: Map[String, Uri]): String =
    update match {
      case s: Update.ForArtifactId =>
        artifactWithOptionalUrl(s.groupId, s.artifactId.name, artifactIdToUrl)
      case g: Update.ForGroupId =>
        g.crossDependencies
          .map(crossDependency =>
            s"* ${artifactWithOptionalUrl(g.groupId, crossDependency.head.artifactId.name, artifactIdToUrl)}\n"
          )
          .mkString_("\n", "", "\n")
    }

  def artifactWithOptionalUrl(
      groupId: GroupId,
      artifactId: String,
      artifactId2Url: Map[String, Uri]
  ): String =
    artifactId2Url.get(artifactId) match {
      case Some(url) => s"[$groupId:$artifactId](${url.renderString})"
      case None      => s"$groupId:$artifactId"
    }

  def oldVersionNote(files: List[String], update: Update): Option[Details] =
    Option.when(files.nonEmpty) {
      val (number, numberWithVersion) = update.on(
        update = u => ("number", s"number (${u.currentVersion})"),
        grouped = _ => ("numbers", "numbers")
      )

      Details(
        s"🔍 Files still referring to the old version $number",
        s"""The following files still refer to the old version $numberWithVersion.
           |You might want to review and update them manually.
           |```
           |${files.mkString("\n")}
           |```
           |""".stripMargin.trim
      )
    }

  def adjustFutureUpdates(update: Update): Details = Details(
    "⚙ Adjust future updates",
    update.on(
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

  def configParsingErrorDetails(error: String): Details =
    Details(
      s"❗ Note that the Scala Steward config file `${RepoConfigAlg.repoConfigBasename}` wasn't parsed correctly",
      s"""|```
          |$error
          |```
          |""".stripMargin.trim
    )

  def migrationNote(scalafixEdits: List[ScalafixEdit]): Option[Details] =
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
      Details("💡 Applied Scalafix Migrations", body)
    }

  def from(
      data: UpdateData,
      branchName: String,
      edits: List[EditAttempt] = List.empty,
      artifactIdToUrl: Map[String, Uri] = Map.empty,
      artifactIdToUpdateInfoUrls: Map[String, List[UpdateInfoUrl]] = Map.empty,
      filesWithOldVersion: List[String] = List.empty,
      addLabels: Boolean = false,
      labels: List[String] = List.empty
  ): NewPullRequestData =
    NewPullRequestData(
      title = CommitMsg
        .replaceVariables(data.repoConfig.commits.messageOrDefault)(
          data.update,
          data.repoData.repo.branch
        )
        .title,
      body = bodyFor(
        data.update,
        edits,
        artifactIdToUrl,
        artifactIdToUpdateInfoUrls,
        filesWithOldVersion,
        data.repoData.cache.maybeRepoConfigParsingError,
        labels
      ),
      head = branchName,
      base = data.baseBranch,
      labels = if (addLabels) labels else List.empty,
      assignees = data.repoConfig.assignees,
      reviewers = data.repoConfig.reviewers
    )

  def updateTypeLabels(anUpdate: Update): List[String] = {
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

    anUpdate.on(u => List(forUpdate(u)), _.updates.map(forUpdate).distinct)
  }

  def labelsFor(
      update: Update,
      edits: List[EditAttempt] = List.empty,
      filesWithOldVersion: List[String] = List.empty,
      artifactIdToVersionScheme: Map[String, String] = Map.empty
  ): List[String] = {
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
        artifactIdToVersionScheme.get(u.mainArtifactId).map(vs => s"version-scheme:$vs")
      List(earlySemVerLabel, semVerSpecLabel, versionSchemeLabel).flatten
    }
    val semverLabels =
      update.on(u => semverForUpdate(u), _.updates.flatMap(semverForUpdate(_)).distinct)

    val artifactMigrationsLabel = Option.when {
      update.asSingleUpdates
        .flatMap(_.forArtifactIds.toList)
        .exists(u => u.newerGroupId.nonEmpty || u.newerArtifactId.nonEmpty)
    }("artifact-migrations")
    val scalafixLabel = edits.collectFirst { case _: ScalafixEdit => "scalafix-migrations" }
    val oldVersionLabel = Option.when(filesWithOldVersion.nonEmpty)("old-version-remains")

    List.concat(
      updateTypeLabels(update),
      semverLabels,
      artifactMigrationsLabel,
      scalafixLabel,
      oldVersionLabel,
      List(commitCountLabel)
    )
  }

  def filterLabels(labels: List[String], includeMatchedLabels: Option[Regex]): List[String] =
    labels.filter(label => includeMatchedLabels.fold(true)(_.matches(label)))

}
