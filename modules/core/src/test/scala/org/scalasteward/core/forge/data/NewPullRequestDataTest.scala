package org.scalasteward.core.forge.data

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAttempt.{ScalafixEdit, UpdateEdit}
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.forge.data.NewPullRequestData._
import org.scalasteward.core.git.{Branch, Commit}
import org.scalasteward.core.nurture.UpdateInfoUrl
import org.scalasteward.core.util.Nel
import org.scalasteward.core.repoconfig.RepoConfig

class NewPullRequestDataTest extends FunSuite {
  test("bodyFor()") {
    val update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single

    val body = bodyFor(
      update = update,
      edits = List.empty,
      artifactIdToUrl = Map.empty,
      artifactIdToUpdateInfoUrls = Map.empty,
      filesWithOldVersion = List.empty,
      configParsingError = None,
      labels = List("library-update")
    )
    val expected =
      s"""|## About this PR
          |📦 Updates ch.qos.logback:logback-classic from `1.2.0` to `1.2.3`
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
          |```
          |updates.ignore = [ { groupId = "ch.qos.logback", artifactId = "logback-classic" } ]
          |```
          |Or, add this to slow down future updates of this dependency:
          |```
          |dependencyOverrides = [{
          |  pullRequests = { frequency = "30 days" },
          |  dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
          |}]
          |```
          |</details>
          |
          |<sup>
          |labels: library-update
          |</sup>""".stripMargin

    assertEquals(body, expected)
  }

  test("bodyFor() with scalafix edit") {
    val update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val scalafixEdit = ScalafixEdit(
      migration = ScalafixMigration(
        groupId = "com.spotify".g,
        artifactIds = Nel.one("scio-core"),
        newVersion = Version("0.7.0"),
        rewriteRules = Nel.of("I am a rewrite rule")
      ),
      result = Right(()),
      maybeCommit = Some(Commit(dummySha1))
    )

    val body = bodyFor(
      update = update,
      edits = List(scalafixEdit),
      artifactIdToUrl = Map.empty,
      artifactIdToUpdateInfoUrls = Map.empty,
      filesWithOldVersion = List.empty,
      configParsingError = None,
      labels = List("library-update")
    )
    val expected =
      s"""|## About this PR
          |📦 Updates ch.qos.logback:logback-classic from `1.2.0` to `1.2.3`
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>💡 Applied Scalafix Migrations</summary>
          |
          |* com.spotify:scio-core:0.7.0
          |  * I am a rewrite rule
          |</details>
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
          |```
          |updates.ignore = [ { groupId = "ch.qos.logback", artifactId = "logback-classic" } ]
          |```
          |Or, add this to slow down future updates of this dependency:
          |```
          |dependencyOverrides = [{
          |  pullRequests = { frequency = "30 days" },
          |  dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
          |}]
          |```
          |</details>
          |
          |<sup>
          |labels: library-update
          |</sup>""".stripMargin

    assertEquals(body, expected)
  }

  test("bodyFor() grouped update") {
    val update1 = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val update2 = ("com.example".g % "foo".a % "1.0.0" %> "2.0.0").single
    val update = Update.Grouped("my-group", Some("The PR title"), List(update1, update2))

    val body = bodyFor(
      update = update,
      edits = List.empty,
      artifactIdToUrl = Map.empty,
      artifactIdToUpdateInfoUrls = Map.empty,
      filesWithOldVersion = List.empty,
      configParsingError = None,
      labels = List("library-update")
    )
    val expected =
      s"""|## About this PR
          |Updates:
          |
          |* 📦 ch.qos.logback:logback-classic from `1.2.0` to `1.2.3`
          |* 📦 com.example:foo from `1.0.0` to `2.0.0` ⚠
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add these to your `.scala-steward.conf` file to ignore future updates of these dependencies:
          |```
          |updates.ignore = [
          |  { groupId = "ch.qos.logback", artifactId = "logback-classic" },
          |  { groupId = "com.example", artifactId = "foo" }
          |]
          |```
          |Or, add these to slow down future updates of these dependencies:
          |```
          |dependencyOverrides = [
          |  {
          |    pullRequests = { frequency = "30 days" },
          |    dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
          |  },
          |  {
          |    pullRequests = { frequency = "30 days" },
          |    dependency = { groupId = "com.example", artifactId = "foo" }
          |  }
          |]
          |```
          |</details>
          |
          |<sup>
          |labels: library-update
          |</sup>""".stripMargin

    assertEquals(body, expected)
  }

  test("bodyFor() output should contain notion about config parsing error") {
    val update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single

    val body = bodyFor(
      update = update,
      edits = List.empty,
      artifactIdToUrl = Map.empty,
      artifactIdToUpdateInfoUrls = Map.empty,
      filesWithOldVersion = List.empty,
      configParsingError = Some("parsing error"),
      labels = List("library-update")
    )
    val expected =
      s"""|## About this PR
          |📦 Updates ch.qos.logback:logback-classic from `1.2.0` to `1.2.3`
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
          |```
          |updates.ignore = [ { groupId = "ch.qos.logback", artifactId = "logback-classic" } ]
          |```
          |Or, add this to slow down future updates of this dependency:
          |```
          |dependencyOverrides = [{
          |  pullRequests = { frequency = "30 days" },
          |  dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
          |}]
          |```
          |</details>
          |<details>
          |<summary>❗ Note that the Scala Steward config file `.scala-steward.conf` wasn't parsed correctly</summary>
          |
          |```
          |parsing error
          |```
          |</details>
          |
          |<sup>
          |labels: library-update
          |</sup>""".stripMargin

    assertEquals(body, expected)
  }

  test("fromTo") {
    val obtained = fromTo(("com.example".g % "foo".a % "1.2.0" %> "1.2.3").single)
    assertEquals(obtained, "from `1.2.0` to `1.2.3`")
  }

  test("links to release notes/changelog") {
    assertEquals(renderUpdateInfoUrls(List.empty), None)

    assertEquals(
      renderUpdateInfoUrls(
        List(UpdateInfoUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"))
      ),
      Some("[Changelog](https://github.com/foo/foo/CHANGELOG.rst)")
    )

    assertEquals(
      renderUpdateInfoUrls(
        List(
          UpdateInfoUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"),
          UpdateInfoUrl.GitHubReleaseNotes(uri"https://github.com/foo/foo/releases/tag/v1.2.3"),
          UpdateInfoUrl.CustomReleaseNotes(
            uri"https://github.com/foo/bar/blob/master/ReleaseNotes.md"
          ),
          UpdateInfoUrl.CustomReleaseNotes(uri"https://github.com/foo/bar/blob/master/Releases.md"),
          UpdateInfoUrl.VersionDiff(uri"https://github.com/foo/foo/compare/v1.2.0...v1.2.3")
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
    val labels = labelsFor(update, edits, List.empty)

    assert(labels.contains("scalafix-migrations"))
    assertEquals(
      appliedMigrations.fold("")(_.toHtml),
      """<details>
        |<summary>💡 Applied Scalafix Migrations</summary>
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
    val labels = labelsFor(update, edits, List.empty)

    assert(labels.contains("scalafix-migrations"))
    assertEquals(
      detail.fold("")(_.toHtml),
      """<details>
        |<summary>💡 Applied Scalafix Migrations</summary>
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
    val labels = labelsFor(update, edits, List.empty)

    assert(labels.contains("scalafix-migrations"))
    assertEquals(
      detail.fold("")(_.toHtml),
      """<details>
        |<summary>💡 Applied Scalafix Migrations</summary>
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
    assertEquals(updateTypeLabels(single), List("library-update"))

    val group = ("com.example".g % Nel.of("foo".a, "bar".a) % "0.1" %> "0.2").group
    assertEquals(updateTypeLabels(group), List("library-update"))

    val testUpdate = (dependency % "test" %> "0.2").single
    assertEquals(updateTypeLabels(testUpdate), List("test-library-update"))

    val sbtPluginUpdate = (dependency.copy(sbtVersion = Some(SbtVersion("1.0"))) %> "0.2").single
    assertEquals(updateTypeLabels(sbtPluginUpdate), List("sbt-plugin-update"))

    val scalafixRuleUpdate = (dependency % "scalafix-rule" %> "0.2").single
    assertEquals(updateTypeLabels(scalafixRuleUpdate), List("scalafix-rule-update"))
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
    val labels = labelsFor(update, List.empty, files)

    assert(labels.contains("old-version-remains"))
    assertEquals(
      note.fold("")(_.toHtml),
      """<details>
        |<summary>🔍 Files still referring to the old version number</summary>
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

    val oneEdit = labelsFor(update, List(updateEdit), List.empty)
    assert(clue(oneEdit).contains("commit-count:1"))

    val twoEdits = labelsFor(update, List(updateEdit, scalafixEdit), List.empty)
    assert(clue(twoEdits).contains("commit-count:n:2"))
  }

  test("regex label filtering") {
    val update = ("a".g % "b".a % "1" -> "2").single
    val updateEdit = UpdateEdit(update, Commit(dummySha1))
    val allLabels = labelsFor(update, List(updateEdit), List.empty)

    val first = filterLabels(allLabels, Some("library-.+".r))
    assertEquals(clue(first), List("library-update"))

    val second = filterLabels(allLabels, Some("(.*update.*)|(.*count.*)".r))
    assertEquals(clue(second), List("library-update", "commit-count:1"))
  }

  test("label for grouped updates add labels for all update types & version changes") {
    val update1 = ("a".g % "b".a % "1" -> "2").single
    val update2 = ("c".g % "d".a % "1.1.0" % "test" %> "1.2.0").single
    val update = Update.Grouped("my-group", None, List(update1, update2))

    val labels = labelsFor(update, Nil, Nil)

    val expected = List(
      "library-update",
      "test-library-update",
      "early-semver-minor",
      "semver-spec-minor",
      "commit-count:0"
    )

    assertEquals(labels, expected)
  }

  test("artifact-migrations label") {
    val update = ("a".g % "b".a % "1" -> "2").single.copy(newerGroupId = Some("aa".g))
    val obtained = labelsFor(update)
    val expected = List("library-update", "artifact-migrations", "commit-count:0")
    assertEquals(obtained, expected)
  }

  test("oldVersionNote doesn't show version for grouped updates") {
    val files = List("Readme.md", "travis.yml")
    val update1 = ("a".g % "b".a % "1" -> "2").single
    val update2 = ("c".g % "d".a % "1.1.0" % "test" %> "1.2.0").single
    val update = Update.Grouped("my-group", None, List(update1, update2))

    val note = oldVersionNote(files, update)

    assertEquals(
      note.fold("")(_.toHtml),
      """<details>
        |<summary>🔍 Files still referring to the old version numbers</summary>
        |
        |The following files still refer to the old version numbers.
        |You might want to review and update them manually.
        |```
        |Readme.md
        |travis.yml
        |```
        |</details>
      """.stripMargin.trim
    )
  }

  test("adjustFutureUpdates for grouped updates shows settings for each update") {
    val update1 = ("a".g % "b".a % "1" -> "2").single
    val update2 = ("c".g % "d".a % "1.1.0" % "test" %> "1.2.0").single
    val update = Update.Grouped("my-group", None, List(update1, update2))

    val note = adjustFutureUpdates(update)

    assertEquals(
      note.toHtml,
      """<details>
        |<summary>⚙ Adjust future updates</summary>
        |
        |Add these to your `.scala-steward.conf` file to ignore future updates of these dependencies:
        |```
        |updates.ignore = [
        |  { groupId = "a", artifactId = "b" },
        |  { groupId = "c", artifactId = "d" }
        |]
        |```
        |Or, add these to slow down future updates of these dependencies:
        |```
        |dependencyOverrides = [
        |  {
        |    pullRequests = { frequency = "30 days" },
        |    dependency = { groupId = "a", artifactId = "b" }
        |  },
        |  {
        |    pullRequests = { frequency = "30 days" },
        |    dependency = { groupId = "c", artifactId = "d" }
        |  }
        |]
        |```
        |</details>
      """.stripMargin.trim
    )
  }

  test("from() should construct NewPullRequestData") {
    val data = UpdateData(
      repoData = RepoData(
        repo = Repo("foo", "bar"),
        cache = dummyRepoCache,
        config = RepoConfig(assignees = List("foo"), reviewers = List("bar"))
      ),
      fork = Repo("scala-steward", "bar"),
      update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
      baseBranch = Branch("main"),
      baseSha1 = dummySha1,
      updateBranch = Branch("update/logback-classic-1.2.3")
    )
    val obtained = from(
      data,
      "scala-steward:update/logback-classic-1.2.3",
      addLabels = true,
      labels = labelsFor(data.update)
    )

    val expectedBody =
      s"""|## About this PR
          |📦 Updates ch.qos.logback:logback-classic from `1.2.0` to `1.2.3`
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
          |```
          |updates.ignore = [ { groupId = "ch.qos.logback", artifactId = "logback-classic" } ]
          |```
          |Or, add this to slow down future updates of this dependency:
          |```
          |dependencyOverrides = [{
          |  pullRequests = { frequency = "30 days" },
          |  dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
          |}]
          |```
          |</details>
          |
          |<sup>
          |labels: library-update, early-semver-patch, semver-spec-patch, commit-count:0
          |</sup>""".stripMargin

    val expected = NewPullRequestData(
      title = "Update logback-classic to 1.2.3",
      body = expectedBody,
      head = "scala-steward:update/logback-classic-1.2.3",
      base = Branch("main"),
      labels = List(
        "library-update",
        "early-semver-patch",
        "semver-spec-patch",
        "commit-count:0"
      ),
      assignees = List("foo"),
      reviewers = List("bar")
    )

    assertEquals(obtained, expected)
  }

  test("from() should construct NewPullRequestData for grouped update") {
    val update1 = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val update2 = ("com.example".g % "foo".a % "1.0.0" %> "2.0.0").single
    val update = Update.Grouped("my-group", None, List(update1, update2))

    val data = UpdateData(
      repoData = RepoData(
        repo = Repo("foo", "bar"),
        cache = dummyRepoCache,
        config = RepoConfig(assignees = List("foo"), reviewers = List("bar"))
      ),
      fork = Repo("scala-steward", "bar"),
      update = update,
      baseBranch = Branch("main"),
      baseSha1 = dummySha1,
      updateBranch = Branch("update/logback-classic-1.2.3")
    )
    val obtained = from(
      data,
      "scala-steward:update/logback-classic-1.2.3",
      addLabels = true,
      labels = labelsFor(data.update)
    )

    val expectedBody =
      s"""|## About this PR
          |Updates:
          |
          |* 📦 ch.qos.logback:logback-classic from `1.2.0` to `1.2.3`
          |* 📦 com.example:foo from `1.0.0` to `2.0.0` ⚠
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add these to your `.scala-steward.conf` file to ignore future updates of these dependencies:
          |```
          |updates.ignore = [
          |  { groupId = "ch.qos.logback", artifactId = "logback-classic" },
          |  { groupId = "com.example", artifactId = "foo" }
          |]
          |```
          |Or, add these to slow down future updates of these dependencies:
          |```
          |dependencyOverrides = [
          |  {
          |    pullRequests = { frequency = "30 days" },
          |    dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
          |  },
          |  {
          |    pullRequests = { frequency = "30 days" },
          |    dependency = { groupId = "com.example", artifactId = "foo" }
          |  }
          |]
          |```
          |</details>
          |
          |<sup>
          |labels: library-update, early-semver-patch, semver-spec-patch, early-semver-major, semver-spec-major, commit-count:0
          |</sup>""".stripMargin

    val expected = NewPullRequestData(
      title = "Update for group my-group",
      body = expectedBody,
      head = "scala-steward:update/logback-classic-1.2.3",
      base = Branch("main"),
      labels = List(
        "library-update",
        "early-semver-patch",
        "semver-spec-patch",
        "early-semver-major",
        "semver-spec-major",
        "commit-count:0"
      ),
      assignees = List("foo"),
      reviewers = List("bar")
    )

    assertEquals(obtained, expected)
  }

  test("should show major upgrade warning sign") {
    val update = ("org.typelevel".g % "cats-effect".a % "2.5.5" %> "3.4.2").single

    val body = bodyFor(
      update = update,
      edits = List.empty,
      artifactIdToUrl = Map.empty,
      artifactIdToUpdateInfoUrls = Map.empty,
      filesWithOldVersion = List.empty,
      configParsingError = None,
      labels = List(
        "library-update",
        "early-semver-major",
        "semver-spec-minor",
        "commit-count:1"
      )
    )
    val expected =
      s"""|## About this PR
          |📦 Updates org.typelevel:cats-effect from `2.5.5` to `3.4.2` ⚠
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
          |```
          |updates.ignore = [ { groupId = "org.typelevel", artifactId = "cats-effect" } ]
          |```
          |Or, add this to slow down future updates of this dependency:
          |```
          |dependencyOverrides = [{
          |  pullRequests = { frequency = "30 days" },
          |  dependency = { groupId = "org.typelevel", artifactId = "cats-effect" }
          |}]
          |```
          |</details>
          |
          |<sup>
          |labels: library-update, early-semver-major, semver-spec-minor, commit-count:1
          |</sup>""".stripMargin

    assertEquals(body, expected)
  }
  test("should not show major upgrade warning sign on a minor update") {
    val update = ("com.lihaoyi".g % "os-lib".a % "0.7.8" %> "0.9.1").single

    val body = bodyFor(
      update = update,
      edits = List.empty,
      artifactIdToUrl = Map.empty,
      artifactIdToUpdateInfoUrls = Map.empty,
      filesWithOldVersion = List.empty,
      configParsingError = None,
      labels = List(
        "library-update",
        "early-semver-major",
        "semver-spec-minor",
        "commit-count:1"
      )
    )
    val expected =
      s"""|## About this PR
          |📦 Updates com.lihaoyi:os-lib from `0.7.8` to `0.9.1`
          |
          |## Usage
          |✅ **Please merge!**
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |_Have a fantastic day writing Scala!_
          |
          |<details>
          |<summary>⚙ Adjust future updates</summary>
          |
          |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
          |```
          |updates.ignore = [ { groupId = "com.lihaoyi", artifactId = "os-lib" } ]
          |```
          |Or, add this to slow down future updates of this dependency:
          |```
          |dependencyOverrides = [{
          |  pullRequests = { frequency = "30 days" },
          |  dependency = { groupId = "com.lihaoyi", artifactId = "os-lib" }
          |}]
          |```
          |</details>
          |
          |<sup>
          |labels: library-update, early-semver-major, semver-spec-minor, commit-count:1
          |</sup>""".stripMargin

    assertEquals(body, expected)
  }
}
