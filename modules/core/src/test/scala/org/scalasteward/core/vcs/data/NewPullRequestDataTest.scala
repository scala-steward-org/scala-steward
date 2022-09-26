package org.scalasteward.core.vcs.data

import io.circe.syntax._
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data.{GroupedUpdate, ReleaseRelatedUrl, RepoData, UpdateData, Version}
import org.scalasteward.core.edit.EditAttempt.{ScalafixEdit, UpdateEdit}
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.git.{Branch, Commit}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.NewPullRequestData._

class NewPullRequestDataTest extends FunSuite {
  test("asJson") {
    val data = UpdateData(
      RepoData(Repo("foo", "bar"), dummyRepoCache, RepoConfig.empty),
      Repo("scala-steward", "bar"),
      ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
      ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
      Branch("master"),
      dummySha1,
      Branch("update/logback-classic-1.2.3")
    )
    val obtained = from(data, "scala-steward:update/logback-classic-1.2.3").asJson.spaces2
    val expected =
      raw"""|{
            |  "title" : "Update logback-classic to 1.2.3",
            |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.\n\nConfigure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Adjust future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\nOr, add this to slow down future updates of this dependency:\n```\ndependencyOverrides = [{\n  pullRequests = { frequency = \"@monthly\" },\n  dependency = { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" }\n}]\n```\n</details>\n\nlabels: library-update, early-semver-patch, semver-spec-patch, commit-count:0",
            |  "head" : "scala-steward:update/logback-classic-1.2.3",
            |  "base" : "master",
            |  "labels" : [
            |    "library-update",
            |    "early-semver-patch",
            |    "semver-spec-patch",
            |    "commit-count:0"
            |  ],
            |  "draft" : false
            |}""".stripMargin
    assertEquals(obtained, expected)
  }

  test("body of pull request data should contain notion about config parsing error") {
    val data = UpdateData(
      RepoData(
        Repo("foo", "bar"),
        dummyRepoCacheWithParsingError,
        RepoConfig.empty
      ),
      Repo("scala-steward", "bar"),
      ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
      ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
      Branch("master"),
      dummySha1,
      Branch("update/logback-classic-1.2.3")
    )
    val obtained = from(data, "scala-steward:update/logback-classic-1.2.3").asJson.spaces2
    val expected =
      raw"""|{
            |  "title" : "Update logback-classic to 1.2.3",
            |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.\n\nConfigure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Adjust future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\nOr, add this to slow down future updates of this dependency:\n```\ndependencyOverrides = [{\n  pullRequests = { frequency = \"@monthly\" },\n  dependency = { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" }\n}]\n```\n</details>\n<details>\n<summary>Note that the Scala Steward config file `.scala-steward.conf` wasn't parsed correctly</summary>\n\n```\nFailed to parse .scala-steward.conf\n```\n</details>\n\nlabels: library-update, early-semver-patch, semver-spec-patch, commit-count:0",
            |  "head" : "scala-steward:update/logback-classic-1.2.3",
            |  "base" : "master",
            |  "labels" : [
            |    "library-update",
            |    "early-semver-patch",
            |    "semver-spec-patch",
            |    "commit-count:0"
            |  ],
            |  "draft" : false
            |}""".stripMargin
    assertEquals(obtained, expected)
  }

  test("fromTo") {
    val obtained = fromTo(("com.example".g % "foo".a % "1.2.0" %> "1.2.3").single)
    assertEquals(obtained, "from 1.2.0 to 1.2.3")
  }

  test("links to release notes/changelog") {
    assertEquals(releaseNote(List.empty), None)

    assertEquals(
      releaseNote(
        List(ReleaseRelatedUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"))
      ),
      Some("[Changelog](https://github.com/foo/foo/CHANGELOG.rst)")
    )

    assertEquals(
      releaseNote(
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
      artifactsWithOptionalUrl(
        ("com.example".g % "foo".a % "1.2.0" %> "1.2.3").single,
        Map("foo" -> uri"https://github.com/foo/foo")
      ),
      "[com.example:foo](https://github.com/foo/foo)"
    )
    assertEquals(
      artifactsWithOptionalUrl(
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
    val appliedMigrations = migrationNote(List.empty)
    assertEquals(appliedMigrations, None)
  }

  test("migrationNote: when artifact has migrations") {
    val scalafixEdit = ScalafixEdit(
      ScalafixMigration(
        "com.spotify".g,
        Nel.one("scio-core"),
        Version("0.7.0"),
        Nel.of("I am a rewrite rule")
      ),
      Right(()),
      Some(Commit(dummySha1))
    )
    val edits = List(scalafixEdit)
    val appliedMigrations = migrationNote(edits)
    val update = ("a".g % "b".a % "1" -> "2").single
    val labels = labelsFor(update, edits, List.empty, None)

    assert(labels.contains("scalafix-migrations"))
    assertEquals(
      appliedMigrations.fold("")(_.toHtml),
      """<details>
        |<summary>Applied Scalafix Migrations</summary>
        |
        |* com.spotify:scio-core:0.7.0
        |  * I am a rewrite rule
        |</details>
      """.stripMargin.trim
    )
  }

  test("migrationNote: when artifact has migrations with docs") {
    val scalafixEdit = ScalafixEdit(
      ScalafixMigration(
        "com.spotify".g,
        Nel.one("scio-core"),
        Version("0.7.0"),
        Nel.of("I am a rewrite rule", "I am a 2nd rewrite rule"),
        Some("https://scalacenter.github.io/scalafix/")
      ),
      Right(()),
      Some(Commit(dummySha1))
    )
    val edits = List(scalafixEdit)
    val detail = migrationNote(edits)
    val update = ("a".g % "b".a % "1" -> "2").single
    val labels = labelsFor(update, edits, List.empty, None)

    assert(labels.contains("scalafix-migrations"))
    assertEquals(
      detail.fold("")(_.toHtml),
      """<details>
        |<summary>Applied Scalafix Migrations</summary>
        |
        |* com.spotify:scio-core:0.7.0
        |  * I am a rewrite rule
        |  * I am a 2nd rewrite rule
        |  * Documentation: https://scalacenter.github.io/scalafix/
        |</details>
      """.stripMargin.trim
    )
  }

  test("migrationNote: with 2 migrations where one didn't produce a change") {
    val scalafixEdit1 = ScalafixEdit(
      ScalafixMigration(
        "com.spotify".g,
        Nel.one("scio-core"),
        Version("0.7.0"),
        Nel.of("I am a rewrite rule", "I am a 2nd rewrite rule"),
        Some("https://scalacenter.github.io/scalafix/")
      ),
      Right(()),
      Some(Commit(dummySha1))
    )
    val scalafixEdit2 = ScalafixEdit(
      ScalafixMigration(
        "org.typeleve".g,
        Nel.of("cats-effect", "cats-effect-laws"),
        Version("3.0.0"),
        Nel.of("I am a rule without an effect")
      ),
      Right(()),
      None
    )
    val edits = List(scalafixEdit1, scalafixEdit2)
    val detail = migrationNote(edits)
    val update = ("a".g % "b".a % "1" -> "2").single
    val labels = labelsFor(update, edits, List.empty, None)

    assert(labels.contains("scalafix-migrations"))
    assertEquals(
      detail.fold("")(_.toHtml),
      """<details>
        |<summary>Applied Scalafix Migrations</summary>
        |
        |* com.spotify:scio-core:0.7.0
        |  * I am a rewrite rule
        |  * I am a 2nd rewrite rule
        |  * Documentation: https://scalacenter.github.io/scalafix/
        |* org.typeleve:{cats-effect,cats-effect-laws}:3.0.0 (created no change)
        |  * I am a rule without an effect
        |</details>
      """.stripMargin.trim
    )
  }

  test("updateType") {
    val dependency = "com.example".g % "foo".a % "0.1"
    val single = (dependency %> "0.2").single
    assertEquals(updateType(single), List("library-update"))

    val group = ("com.example".g % Nel.of("foo".a, "bar".a) % "0.1" %> "0.2").group
    assertEquals(updateType(group), List("library-update"))

    val testUpdate = (dependency % "test" %> "0.2").single
    assertEquals(updateType(testUpdate), List("test-library-update"))

    val sbtPluginUpdate = (dependency.copy(sbtVersion = Some(SbtVersion("1.0"))) %> "0.2").single
    assertEquals(updateType(sbtPluginUpdate), List("sbt-plugin-update"))

    val scalafixRuleUpdate = (dependency % "scalafix-rule" %> "0.2").single
    assertEquals(updateType(scalafixRuleUpdate), List("scalafix-rule-update"))
  }

  test("oldVersionNote without files") {
    val files = List.empty
    val update = ("com.example".g % "foo".a % "0.1" %> "0.2").single
    assertEquals(oldVersionNote(files, update), None)
  }

  test("oldVersionNote with files") {
    val files = List("Readme.md", "travis.yml")
    val update = ("com.example".g % "foo".a % "0.1" %> "0.2").single
    val note = oldVersionNote(files, update)
    val labels = labelsFor(update, List.empty, files, None)

    assert(labels.contains("old-version-remains"))
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

  test("commit-count label") {
    val update = ("a".g % "b".a % "1" -> "2").single
    val updateEdit = UpdateEdit(update, Commit(dummySha1))
    val scalafixEdit = ScalafixEdit(
      ScalafixMigration(
        "com.spotify".g,
        Nel.one("scio-core"),
        Version("0.7.0"),
        Nel.of("I am a rewrite rule", "I am a 2nd rewrite rule"),
        Some("https://scalacenter.github.io/scalafix/")
      ),
      Right(()),
      Some(Commit(dummySha1))
    )

    val oneEdit = labelsFor(update, List(updateEdit), List.empty, None)
    assert(clue(oneEdit).contains("commit-count:1"))

    val twoEdits = labelsFor(update, List(updateEdit, scalafixEdit), List.empty, None)
    assert(clue(twoEdits).contains("commit-count:n:2"))
  }

  test("regex label filtering") {
    val update = ("a".g % "b".a % "1" -> "2").single
    val updateEdit = UpdateEdit(update, Commit(dummySha1))

    val first = labelsFor(update, List(updateEdit), List.empty, Some("library-.+".r))
    assertEquals(clue(first), List("library-update"))

    val second = labelsFor(update, List(updateEdit), List.empty, Some("(.*update.*)|(.*count.*)".r))
    assertEquals(clue(second), List("library-update", "commit-count:1"))
  }

  test("label for grouped updates add labels for all update types & version changes") {
    val update1 = ("a".g % "b".a % "1" -> "2").single
    val update2 = ("c".g % "d".a % "1.1.0" % "test" %> "1.2.0").single
    val update = GroupedUpdate("my-group", None, List(update1, update2))

    val labels = labelsFor(update, Nil, Nil, None)

    val expected = List(
      "library-update",
      "test-library-update",
      "early-semver-minor",
      "semver-spec-minor",
      "commit-count:0"
    )

    assertEquals(labels, expected)
  }

}
