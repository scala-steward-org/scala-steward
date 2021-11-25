package org.scalasteward.core.vcs.data

import io.circe.syntax._
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data._
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel

class NewPullRequestDataTest extends FunSuite {
  test("asJson") {
    val data = UpdateData(
      RepoData(Repo("foo", "bar"), dummyRepoCache, RepoConfig.empty),
      Repo("scala-steward", "bar"),
      ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
      Branch("master"),
      Sha1(Sha1.HexString.unsafeFrom("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
      Branch("update/logback-classic-1.2.3")
    )
    val obtained = NewPullRequestData
      .from(data, "scala-steward:update/logback-classic-1.2.3")
      .asJson
      .spaces2
    val expected =
      raw"""|{
            |  "title" : "Update logback-classic to 1.2.3",
            |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.\n\nConfigure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Ignore future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\n</details>\n\nlabels: library-update, early-semver-patch, semver-spec-patch",
            |  "head" : "scala-steward:update/logback-classic-1.2.3",
            |  "base" : "master",
            |  "draft" : false
            |}""".stripMargin
    assertEquals(obtained, expected)
  }

  test("fromTo") {
    assertEquals(
      NewPullRequestData.fromTo(("com.example".g % "foo".a % "1.2.0" %> "1.2.3").single),
      "from 1.2.0 to 1.2.3"
    )
  }

  test("links to release notes/changelog") {
    assertEquals(NewPullRequestData.releaseNote(List.empty), None)

    assertEquals(
      NewPullRequestData.releaseNote(
        List(ReleaseRelatedUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"))
      ),
      Some("[Changelog](https://github.com/foo/foo/CHANGELOG.rst)")
    )

    assertEquals(
      NewPullRequestData.releaseNote(
        List(
          ReleaseRelatedUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"),
          ReleaseRelatedUrl.GitHubReleaseNotes(uri"https://github.com/foo/foo/releases/tag/v1.2.3"),
          ReleaseRelatedUrl.CustomReleaseNotes(
            uri"https://github.com/foo/bar/blob/master/ReleaseNotes.md"
          ),
          ReleaseRelatedUrl.CustomReleaseNotes(
            uri"https://github.com/foo/bar/blob/master/Releases.md"
          ),
          ReleaseRelatedUrl.VersionDiff(uri"https://github.com/foo/foo/compare/v1.2.0...v1.2.3")
        )
      ),
      Some(
        "[Changelog](https://github.com/foo/foo/CHANGELOG.rst) - [GitHub Release Notes](https://github.com/foo/foo/releases/tag/v1.2.3) - [Release Notes](https://github.com/foo/bar/blob/master/ReleaseNotes.md) - [Release Notes](https://github.com/foo/bar/blob/master/Releases.md) - [Version Diff](https://github.com/foo/foo/compare/v1.2.0...v1.2.3)"
      )
    )
  }

  test("showing artifacts with URL in Markdown format") {
    assertEquals(
      NewPullRequestData.artifactsWithOptionalUrl(
        ("com.example".g % "foo".a % "1.2.0" %> "1.2.3").single,
        Map("foo" -> uri"https://github.com/foo/foo")
      ),
      "[com.example:foo](https://github.com/foo/foo)"
    )
    assertEquals(
      NewPullRequestData.artifactsWithOptionalUrl(
        ("com.example".g % Nel.of("foo".a, "bar".a) % "1.2.0" %> "1.2.3").group,
        Map("foo" -> uri"https://github.com/foo/foo", "bar" -> uri"https://github.com/bar/bar")
      ),
      """
        |* [com.example:foo](https://github.com/foo/foo)
        |* [com.example:bar](https://github.com/bar/bar)
        |
        |""".stripMargin
    )
  }

  test("migrationNote: when no migrations") {
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List.empty)

    assertEquals(label, None)
    assertEquals(appliedMigrations, None)
  }

  test("migrationNote: when artifact has migrations") {
    val update = ("com.spotify".g % "scio-core".a % "0.6.0" %> "0.7.0").single
    val migration = ScalafixMigration(
      update.groupId,
      Nel.of(update.artifactId.name),
      Version("0.7.0"),
      Nel.of("I am a rewrite rule")
    )
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List(migration))

    assertEquals(label, Some("scalafix-migrations"))
    assertEquals(
      appliedMigrations.fold("")(_.toHtml),
      """<details>
        |<summary>Applied Migrations</summary>
        |
        |* I am a rewrite rule
        |</details>
      """.stripMargin.trim
    )
  }

  test("migrationNote: when artifact has migrations with docs") {
    val update = ("com.spotify".g % "scio-core".a % "0.6.0" %> "0.7.0").single
    val migration = ScalafixMigration(
      update.groupId,
      Nel.of(update.artifactId.name),
      Version("0.7.0"),
      Nel.of("I am a rewrite rule"),
      Some("https://scalacenter.github.io/scalafix/")
    )
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List(migration))

    assertEquals(label, Some("scalafix-migrations"))
    assertEquals(
      appliedMigrations.fold("")(_.toHtml),
      """<details>
        |<summary>Applied Migrations</summary>
        |
        |* I am a rewrite rule
        |
        |Documentation:
        |
        |* https://scalacenter.github.io/scalafix/
        |</details>
      """.stripMargin.trim
    )
  }

  test("updateType") {
    val dependency = "com.example".g % "foo".a % "0.1"
    val single = (dependency %> "0.2").single
    val group = ("com.example".g % Nel.of("foo".a, "bar".a) % "0.1" %> "0.2").group
    assertEquals(NewPullRequestData.updateType(single), "library-update")
    assertEquals(NewPullRequestData.updateType(group), "library-update")

    assertEquals(
      NewPullRequestData.updateType((dependency % "test" %> "0.2").single),
      "test-library-update"
    )
    assertEquals(
      NewPullRequestData.updateType(
        (dependency.copy(sbtVersion = Some(SbtVersion("1.0"))) %> "0.2").single
      ),
      "sbt-plugin-update"
    )
    assertEquals(
      NewPullRequestData.updateType((dependency % "scalafix-rule" %> "0.2").single),
      "scalafix-rule-update"
    )
  }

  test("oldVersionNote without files") {
    val files = List.empty
    val update = ("com.example".g % "foo".a % "0.1" %> "0.2").single

    assertEquals(NewPullRequestData.oldVersionNote(files, update), (None, None))
  }

  test("oldVersionNote with files") {
    val files = List("Readme.md", "travis.yml")
    val update = ("com.example".g % "foo".a % "0.1" %> "0.2").single

    val (label, note) = NewPullRequestData.oldVersionNote(files, update)

    assertEquals(label, Some("old-version-remains"))
    assertEquals(
      note.fold("")(_.toHtml),
      """<details>
        |<summary>Files still referring to the old version number</summary>
        |
        |The following files still refer to the old version number (0.1).
        |You might want to review and update them manually.
        |```
        |Readme.md
        |travis.yml
        |```
        |</details>
      """.stripMargin.trim
    )
  }
}
