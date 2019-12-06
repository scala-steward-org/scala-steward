/*
 * Copyright 2018-2019 Scala Steward contributors
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

import cats.implicits._
import io.circe.Encoder
import io.circe.generic.semiauto._
import org.scalasteward.core.data.{GroupId, SemVer, Update}
import org.scalasteward.core.git
import org.scalasteward.core.git.Branch
import org.scalasteward.core.nurture.UpdateData
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.{Details, Nel}

final case class NewPullRequestData(
    title: String,
    body: String,
    head: String,
    base: Branch
)

object NewPullRequestData {
  implicit val newPullRequestDataEncoder: Encoder[NewPullRequestData] =
    deriveEncoder

  def bodyFor(
      update: Update,
      artifactIdToUrl: Map[String, String],
      branchCompareUrl: Option[String],
      releaseNoteUrl: Option[String],
      migrations: List[Migration]
  ): String = {
    val artifacts = artifactsWithOptionalUrl(update, artifactIdToUrl)
    val (migrationLabel, appliedMigrations) = migrationNote(update, migrations)
    val details = ignoreFutureUpdates(update) :: appliedMigrations.toList
    val labels = Nel.fromList(semVerLabel(update).toList ++ migrationLabel.toList)

    s"""|Updates ${artifacts} ${fromTo(update, branchCompareUrl)}.
        |${releaseNote(releaseNoteUrl).getOrElse("")}
        |
        |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
        |
        |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
        |
        |Have a fantastic day writing Scala!
        |
        |${details.map(_.toHtml).mkString("\n")}
        |
        |${labels.fold("")(_.mkString_("labels: ", ", ", ""))}
        |""".stripMargin.trim
  }

  def releaseNote(releaseNoteUrl: Option[String]): Option[String] =
    releaseNoteUrl.map { url =>
      s"[Release Notes/Changelog](${url})"
    }

  def fromTo(update: Update, branchCompareUrl: Option[String]): String = {
    val fromToVersions = s"from ${update.currentVersion} to ${update.nextVersion}"
    branchCompareUrl match {
      case None             => fromToVersions
      case Some(compareUrl) => s"[${fromToVersions}](${compareUrl})"
    }
  }

  def artifactsWithOptionalUrl(update: Update, artifactIdToUrl: Map[String, String]): String =
    update match {
      case s: Update.Single => artifactWithOptionalUrl(s.groupId, s.artifactId, artifactIdToUrl)
      case g: Update.Group =>
        g.artifactIds
          .map(artifactId =>
            s"* ${artifactWithOptionalUrl(g.groupId, artifactId, artifactIdToUrl)}\n"
          )
          .mkString_("\n", "", "\n")
    }

  def artifactWithOptionalUrl(
      groupId: GroupId,
      artifactId: String,
      artifactId2Url: Map[String, String]
  ): String =
    artifactId2Url.get(artifactId) match {
      case Some(url) => s"[${groupId}:${artifactId}](${url})"
      case None      => s"${groupId}:${artifactId}"
    }

  def ignoreFutureUpdates(update: Update): Details =
    Details(
      "Ignore future updates",
      s"""|Add this to your `${RepoConfigAlg.repoConfigBasename}` file to ignore future updates of this dependency:
          |```
          |${RepoConfigAlg.configToIgnoreFurtherUpdates(update)}
          |```
          |""".stripMargin.trim
    )

  def migrationNote(
      update: Update,
      migrations: List[Migration]
  ): (Option[String], Option[Details]) = {
    update.artifactId
    if (migrations.isEmpty)
      (None, None)
    else
      (
        Some("scalafix-migrations"),
        Some(
          Details(
            "Applied Migrations",
            migrations.flatMap(_.rewriteRules.toList).map(rule => s"* $rule").mkString("\n")
          )
        )
      )
  }

  def semVerLabel(update: Update): Option[String] =
    for {
      curr <- SemVer.parse(update.currentVersion)
      next <- SemVer.parse(update.nextVersion)
      change <- SemVer.getChange(curr, next)
    } yield s"semver-${change.render}"

  def from(
      data: UpdateData,
      branchName: String,
      artifactIdToUrl: Map[String, String] = Map.empty,
      branchCompareUrl: Option[String] = None,
      releaseNoteUrl: Option[String] = None,
      migrations: List[Migration] = List.empty
  ): NewPullRequestData =
    NewPullRequestData(
      title = git.commitMsgFor(data.update),
      body = bodyFor(
        data.update,
        artifactIdToUrl,
        branchCompareUrl,
        releaseNoteUrl,
        migrations
      ),
      head = branchName,
      base = data.baseBranch
    )
}
