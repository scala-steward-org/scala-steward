/*
 * Copyright 2018-2022 Scala Steward contributors
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

package org.scalasteward.core.vcs.data

import cats.syntax.all._
import io.circe.Encoder
import io.circe.generic.semiauto._
import org.http4s.Uri
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAttempt
import org.scalasteward.core.edit.EditAttempt.ScalafixEdit
import org.scalasteward.core.git
import org.scalasteward.core.git.Branch
import org.scalasteward.core.repoconfig.{GroupRepoConfig, RepoConfigAlg}
import org.scalasteward.core.util.{Details, Nel}

import scala.util.matching.Regex

final case class NewPullRequestData(
    title: String,
    body: String,
    head: String,
    base: Branch,
    labels: List[String],
    draft: Boolean = false
)

object NewPullRequestData {
  implicit val newPullRequestDataEncoder: Encoder[NewPullRequestData] =
    deriveEncoder

  def bodyFor(
      update: Update,
      edits: List[EditAttempt],
      artifactIdToUrl: Map[String, Uri],
      releaseRelatedUrls: List[ReleaseRelatedUrl],
      filesWithOldVersion: List[String],
      configParsingError: Option[String],
      labels: List[String]
  ): String = {
    val artifacts = artifactsWithOptionalUrl(update, artifactIdToUrl)
    val migrations = edits.collect { case scalafixEdit: ScalafixEdit => scalafixEdit }
    val appliedMigrations = migrationNote(migrations)
    val oldVersionDetails = oldVersionNote(filesWithOldVersion, update)
    val details = List(
      appliedMigrations,
      oldVersionDetails,
      adjustFutureUpdates(update).some,
      configParsingError.map(configParsingErrorDetails)
    ).flatten

    s"""|Updates $artifacts ${fromTo(update)}.
        |${releaseNote(releaseRelatedUrls).getOrElse("")}
        |
        |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
        |
        |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
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

  def releaseNote(releaseRelatedUrls: List[ReleaseRelatedUrl]): Option[String] =
    Option.when(releaseRelatedUrls.nonEmpty) {
      releaseRelatedUrls
        .map {
          case ReleaseRelatedUrl.CustomChangelog(url) =>
            s"[Changelog](${url.renderString})"
          case ReleaseRelatedUrl.CustomReleaseNotes(url) =>
            s"[Release Notes](${url.renderString})"
          case ReleaseRelatedUrl.GitHubReleaseNotes(url) =>
            s"[GitHub Release Notes](${url.renderString})"
          case ReleaseRelatedUrl.VersionDiff(url) =>
            s"[Version Diff](${url.renderString})"
        }
        .mkString(" - ")
    }

  def fromTo(update: Update): String =
    s"from ${update.currentVersion} to ${update.nextVersion}"

  def artifactsWithOptionalUrl(update: Update, artifactIdToUrl: Map[String, Uri]): String =
    update match {
      case s: Update.Single =>
        artifactWithOptionalUrl(s.groupId, s.artifactId.name, artifactIdToUrl)
      case g: Update.Group =>
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
      Details(
        "Files still referring to the old version number",
        s"""The following files still refer to the old version number (${update.currentVersion}).
           |You might want to review and update them manually.
           |```
           |${files.mkString("\n")}
           |```
           |""".stripMargin.trim
      )
    }

  def adjustFutureUpdates(update: Update): Details =
    Details(
      "Adjust future updates",
      s"""|Add this to your `${RepoConfigAlg.repoConfigBasename}` file to ignore future updates of this dependency:
          |```
          |${RepoConfigAlg.configToIgnoreFurtherUpdates(update)}
          |```
          |Or, add this to slow down future updates of this dependency:
          |```
          |${GroupRepoConfig.configToSlowDownUpdatesFrequency(update)}
          |```
          |""".stripMargin.trim
    )

  def configParsingErrorDetails(error: String): Details =
    Details(
      s"Note that the Scala Steward config file `${RepoConfigAlg.repoConfigBasename}` wasn't parsed correctly",
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
      Details("Applied Scalafix Migrations", body)
    }

  def from(
      data: UpdateData,
      branchName: String,
      edits: List[EditAttempt] = List.empty,
      artifactIdToUrl: Map[String, Uri] = Map.empty,
      releaseRelatedUrls: List[ReleaseRelatedUrl] = List.empty,
      filesWithOldVersion: List[String] = List.empty,
      labelRegex: Option[Regex] = None
  ): NewPullRequestData = {
    val labels = labelsFor(data.update, edits, filesWithOldVersion, labelRegex)
    NewPullRequestData(
      title = git
        .commitMsgFor(data.update, data.repoConfig.commits, data.repoData.repo.branch)
        .title,
      body = bodyFor(
        data.update,
        edits,
        artifactIdToUrl,
        releaseRelatedUrls,
        filesWithOldVersion,
        data.repoData.cache.maybeRepoConfigParsingError,
        labels
      ),
      head = branchName,
      base = data.baseBranch,
      labels = labels
    )
  }

  def updateType(update: Update): String = {
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

  def labelsFor(
      update: Update,
      edits: List[EditAttempt],
      filesWithOldVersion: List[String],
      labelRegex: Option[Regex]
  ): List[String] = {
    val commitCount = edits.flatMap(_.maybeCommit).size
    val commitCountLabel = "commit-count:" + (commitCount match {
      case n if n <= 1 => s"$n"
      case n           => s"n:$n"
    })
    val semVerVersions =
      (SemVer.parse(update.currentVersion.value), SemVer.parse(update.nextVersion.value)).tupled
    val earlySemVerLabel = semVerVersions.flatMap { case (curr, next) =>
      SemVer.getChangeEarly(curr, next).map(c => s"early-semver-${c.render}")
    }
    val semVerSpecLabel = semVerVersions.flatMap { case (curr, next) =>
      SemVer.getChangeSpec(curr, next).map(c => s"semver-spec-${c.render}")
    }
    val scalafixLabel = edits.collectFirst { case _: ScalafixEdit => "scalafix-migrations" }
    val oldVersionLabel = Option.when(filesWithOldVersion.nonEmpty)("old-version-remains")

    val allLabels = updateType(update) ::
      List(earlySemVerLabel, semVerSpecLabel, scalafixLabel, oldVersionLabel).flatten ++
      List(commitCountLabel)

    allLabels.filter(label => labelRegex.fold(true)(_.matches(label)))
  }
}
