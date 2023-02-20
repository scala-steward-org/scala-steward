package org.scalasteward.core.forge.data

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data._
import org.scalasteward.core.edit.EditAttempt.ScalafixEdit
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.forge.data.NewPullRequestData._
import org.scalasteward.core.git.{Branch, Commit}
import org.scalasteward.core.nurture.UpdateInfoUrl
import org.scalasteward.core.util.Nel
import org.scalasteward.core.repoconfig.RepoConfig

class NewPullRequestDataTest extends FunSuite {
  private val updateData = UpdateData(
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

  test("body()") {
    val newPullRequestData = NewPullRequestData(updateData, "main")

    val body = newPullRequestData.body
    val expected =
      s"""|Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.
          |
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |Have a fantastic day writing Scala!
          |
          |<details>
          |<summary>Adjust future updates</summary>
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
          |labels: library-update""".stripMargin

    assertEquals(body, expected)
  }

  test("body() with scalafix edit") {
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
    val newPullRequestData = NewPullRequestData(updateData, "main", List(scalafixEdit))

    val body = newPullRequestData.body
    val expected =
      s"""|Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.
          |
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |Have a fantastic day writing Scala!
          |
          |<details>
          |<summary>Applied Scalafix Migrations</summary>
          |
          |* com.spotify:scio-core:0.7.0
          |  * I am a rewrite rule
          |</details>
          |<details>
          |<summary>Adjust future updates</summary>
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
          |labels: library-update""".stripMargin

    assertEquals(body, expected)
  }

  test("body() groupped update") {
    val update1 = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val update2 = ("com.example".g % "foo".a % "1.0.0" %> "2.0.0").single
    val update = Update.Grouped("my-group", Some("The PR title"), List(update1, update2))
    val newPullRequestData = NewPullRequestData(updateData.copy(update = update), "main")

    val body = newPullRequestData.body
    val expected =
      s"""|Updates:
          |
          |* ch.qos.logback:logback-classic from 1.2.0 to 1.2.3
          |* com.example:foo from 1.0.0 to 2.0.0
          |
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |Have a fantastic day writing Scala!
          |
          |<details>
          |<summary>Adjust future updates</summary>
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
          |labels: library-update""".stripMargin

    assertEquals(body, expected)
  }

  test("body() output should contain notion about config parsing error") {
    val newPullRequestData = NewPullRequestData(
      updateData.copy(repoData =
        updateData.repoData.copy(cache =
          updateData.repoData.cache.copy(maybeRepoConfigParsingError = Some("parsing error"))
        )
      ),
      "main"
    )

    val body = newPullRequestData.body
    val expected =
      s"""|Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.
          |
          |
          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
          |
          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
          |
          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
          |
          |Have a fantastic day writing Scala!
          |
          |<details>
          |<summary>Adjust future updates</summary>
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
          |<summary>Note that the Scala Steward config file `.scala-steward.conf` wasn't parsed correctly</summary>
          |
          |```
          |parsing error
          |```
          |</details>
          |
          |labels: library-update""".stripMargin

    assertEquals(body, expected)
  }

  test("fromTo") {
    val obtained = fromTo(("com.example".g % "foo".a % "1.2.0" %> "1.2.3").single)
    assertEquals(obtained, "from 1.2.0 to 1.2.3")
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
    val newPullRequestData = NewPullRequestData(updateData, "main")
    val appliedMigrations = newPullRequestData.migrationNote
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
    val newPullRequestData = NewPullRequestData(updateData, "main", edits)
    val appliedMigrations = newPullRequestData.migrationNote
    val labels = newPullRequestData.labels

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
    val newPullRequestData = NewPullRequestData(updateData, "main", edits)
    val detail = newPullRequestData.migrationNote
    val labels = newPullRequestData.labels

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
    val newPullRequestData = NewPullRequestData(updateData, "main", edits)
    val detail = newPullRequestData.migrationNote
    val labels = newPullRequestData.labels

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
    val dataForSingle = NewPullRequestData(updateData.copy(update = single), "main")
    assertEquals(dataForSingle.updateTypeLabels, List("library-update"))

    val group = ("com.example".g % Nel.of("foo".a, "bar".a) % "0.1" %> "0.2").group
    val dataForGroup = NewPullRequestData(updateData.copy(update = group), "main")
    assertEquals(dataForGroup.updateTypeLabels, List("library-update"))

    val testUpdate = (dependency % "test" %> "0.2").single
    val dataForTestUpdate = NewPullRequestData(updateData.copy(update = testUpdate), "main")
    assertEquals(dataForTestUpdate.updateTypeLabels, List("test-library-update"))

    val sbtPluginUpdate = (dependency.copy(sbtVersion = Some(SbtVersion("1.0"))) %> "0.2").single
    val dataForSbtPluginUpdate =
      NewPullRequestData(updateData.copy(update = sbtPluginUpdate), "main")
    assertEquals(dataForSbtPluginUpdate.updateTypeLabels, List("sbt-plugin-update"))

    val scalafixRuleUpdate = (dependency % "scalafix-rule" %> "0.2").single
    val dataForScalafixRuleUpdate =
      NewPullRequestData(updateData.copy(update = scalafixRuleUpdate), "main")
    assertEquals(dataForScalafixRuleUpdate.updateTypeLabels, List("scalafix-rule-update"))
  }

  test("oldVersionNote without files") {
    val files = List.empty
    val newPullRequestData = NewPullRequestData(updateData, "main", filesWithOldVersion = files)
    assertEquals(newPullRequestData.oldVersionNote, None)
  }

  test("oldVersionNote with files") {
    val files = List("Readme.md", "travis.yml")
    val newPullRequestData = NewPullRequestData(updateData, "main", filesWithOldVersion = files)
    val note = newPullRequestData.oldVersionNote
    val labels = newPullRequestData.labels

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

    val oneEdit = NewPullRequestData(updateData, "main")
    assert(clue(oneEdit.labels).contains("commit-count:1"))

    val twoEdits = NewPullRequestData(updateData, "main", edits = List(scalafixEdit))
    assert(clue(twoEdits.labels).contains("commit-count:n:2"))
  }

  test("regex label filtering") {
    val allLabels = NewPullRequestData(updateData, "main").labels

    val first = NewPullRequestData(
      updateData.copy(repoData =
        updateData.repoData.copy(config =
          updateData.repoData.config.copy(pullRequests =
            updateData.repoData.config.pullRequests
              .copy(includeMatchedLabels = Some("library-.+".r))
          )
        )
      ),
      "main"
    ).labels
    assertEquals(clue(first), List("library-update"))

    val second = NewPullRequestData(
      updateData.copy(repoData =
        updateData.repoData.copy(config =
          updateData.repoData.config.copy(pullRequests =
            updateData.repoData.config.pullRequests
              .copy(includeMatchedLabels = Some("(.*update.*)|(.*count.*)".r))
          )
        )
      ),
      "main"
    ).labels
    assertEquals(clue(second), List("library-update", "commit-count:1"))
  }

  test("label for grouped updates add labels for all update types & version changes") {
    val update1 = ("a".g % "b".a % "1" -> "2").single
    val update2 = ("c".g % "d".a % "1.1.0" % "test" %> "1.2.0").single
    val update = Update.Grouped("my-group", None, List(update1, update2))
    val newPullRequestData = NewPullRequestData(updateData.copy(update = update), "main")

    val labels = newPullRequestData.labels

    val expected = List(
      "library-update",
      "test-library-update",
      "early-semver-minor",
      "semver-spec-minor",
      "commit-count:0"
    )

    assertEquals(labels, expected)
  }

  test("oldVersionNote doesn't show version for grouped updates") {
    val files = List("Readme.md", "travis.yml")
    val update1 = ("a".g % "b".a % "1" -> "2").single
    val update2 = ("c".g % "d".a % "1.1.0" % "test" %> "1.2.0").single
    val update = Update.Grouped("my-group", None, List(update1, update2))
    val newPullRequestData =
      NewPullRequestData(updateData.copy(update = update), "main", filesWithOldVersion = files)

    val note = newPullRequestData.oldVersionNote

    assertEquals(
      note.fold("")(_.toHtml),
      """<details>
        |<summary>Files still referring to the old version numbers</summary>
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

  test("adjustFutureUpdates for grouped udpates shows settings for each update") {
    val update1 = ("a".g % "b".a % "1" -> "2").single
    val update2 = ("c".g % "d".a % "1.1.0" % "test" %> "1.2.0").single
    val update = Update.Grouped("my-group", None, List(update1, update2))
    val newPullRequestData = NewPullRequestData(updateData.copy(update = update), "main")

    val note = newPullRequestData.adjustFutureUpdates

    assertEquals(
      note.toHtml,
      """<details>
        |<summary>Adjust future updates</summary>
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

//  test("from() should construct NewPullRequestData") {
//    val obtained = from(
//      data,
//      "scala-steward:update/logback-classic-1.2.3",
//      labels = labelsFor(data.update)
//    )
//
//    val expectedBody =
//      s"""|Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.
//          |
//          |
//          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
//          |
//          |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
//          |
//          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
//          |
//          |Have a fantastic day writing Scala!
//          |
//          |<details>
//          |<summary>Adjust future updates</summary>
//          |
//          |Add this to your `.scala-steward.conf` file to ignore future updates of this dependency:
//          |```
//          |updates.ignore = [ { groupId = "ch.qos.logback", artifactId = "logback-classic" } ]
//          |```
//          |Or, add this to slow down future updates of this dependency:
//          |```
//          |dependencyOverrides = [{
//          |  pullRequests = { frequency = "30 days" },
//          |  dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
//          |}]
//          |```
//          |</details>
//          |
//          |labels: library-update, early-semver-patch, semver-spec-patch, commit-count:0""".stripMargin
//
//    val expected = NewPullRequestData(
//      title = "Update logback-classic to 1.2.3",
//      body = expectedBody,
//      head = "scala-steward:update/logback-classic-1.2.3",
//      base = Branch("main"),
//      labels = List(
//        "library-update",
//        "early-semver-patch",
//        "semver-spec-patch",
//        "commit-count:0"
//      ),
//      assignees = List("foo"),
//      reviewers = List("bar")
//    )
//
//    assertEquals(obtained, expected)
//  }
//
//  test("from() should construct NewPullRequestData for groupped update") {
//    val update1 = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
//    val update2 = ("com.example".g % "foo".a % "1.0.0" %> "2.0.0").single
//    val update = Update.Grouped("my-group", None, List(update1, update2))
//
//    val data = UpdateData(
//      repoData = RepoData(
//        repo = Repo("foo", "bar"),
//        cache = dummyRepoCache,
//        config = RepoConfig(assignees = List("foo"), reviewers = List("bar"))
//      ),
//      fork = Repo("scala-steward", "bar"),
//      update = update,
//      baseBranch = Branch("main"),
//      baseSha1 = dummySha1,
//      updateBranch = Branch("update/logback-classic-1.2.3")
//    )
//    val obtained = from(
//      data,
//      "scala-steward:update/logback-classic-1.2.3",
//      labels = labelsFor(data.update)
//    )
//
//    val expectedBody =
//      s"""|Updates:
//          |
//          |* ch.qos.logback:logback-classic from 1.2.0 to 1.2.3
//          |* com.example:foo from 1.0.0 to 2.0.0
//          |
//          |
//          |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
//          |
//          |If you have any feedback, just mention me in the comments below.
//          |
//          |Configure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
//          |
//          |Have a fantastic day writing Scala!
//          |
//          |<details>
//          |<summary>Adjust future updates</summary>
//          |
//          |Add these to your `.scala-steward.conf` file to ignore future updates of these dependencies:
//          |```
//          |updates.ignore = [
//          |  { groupId = "ch.qos.logback", artifactId = "logback-classic" },
//          |  { groupId = "com.example", artifactId = "foo" }
//          |]
//          |```
//          |Or, add these to slow down future updates of these dependencies:
//          |```
//          |dependencyOverrides = [
//          |  {
//          |    pullRequests = { frequency = "30 days" },
//          |    dependency = { groupId = "ch.qos.logback", artifactId = "logback-classic" }
//          |  },
//          |  {
//          |    pullRequests = { frequency = "30 days" },
//          |    dependency = { groupId = "com.example", artifactId = "foo" }
//          |  }
//          |]
//          |```
//          |</details>
//          |
//          |labels: library-update, early-semver-patch, semver-spec-patch, early-semver-major, semver-spec-major, commit-count:0""".stripMargin
//
//    val expected = NewPullRequestData(
//      title = "Update for group my-group",
//      body = expectedBody,
//      head = "scala-steward:update/logback-classic-1.2.3",
//      base = Branch("main"),
//      labels = List(
//        "library-update",
//        "early-semver-patch",
//        "semver-spec-patch",
//        "early-semver-major",
//        "semver-spec-major",
//        "commit-count:0"
//      ),
//      assignees = List("foo"),
//      reviewers = List("bar")
//    )
//
//    assertEquals(obtained, expected)
//  }
}
