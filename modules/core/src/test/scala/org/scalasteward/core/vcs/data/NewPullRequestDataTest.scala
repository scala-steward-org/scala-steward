package org.scalasteward.core.vcs.data

import io.circe.syntax._
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.nurture.UpdateData
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NewPullRequestDataTest extends AnyFunSuite with Matchers {
  test("asJson") {
    val data = UpdateData(
      Repo("foo", "bar"),
      Repo("scala-steward", "bar"),
      RepoConfig(),
      Update.Single(GroupId("ch.qos.logback"), "logback-classic", "1.2.0", Nel.of("1.2.3")),
      Branch("master"),
      Sha1(Sha1.HexString("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
      Branch("update/logback-classic-1.2.3")
    )
    NewPullRequestData
      .from(data, "scala-steward:update/logback-classic-1.2.3")
      .asJson
      .spaces2 shouldBe
      """|{
         |  "title" : "Update logback-classic to 1.2.3",
         |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Ignore future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\n</details>\n\nlabels: semver-patch",
         |  "head" : "scala-steward:update/logback-classic-1.2.3",
         |  "base" : "master"
         |}
         |""".stripMargin.trim
  }

  test("fromTo") {
    NewPullRequestData.fromTo(
      Update.Single(GroupId("com.example"), "foo", "1.2.0", Nel.of("1.2.3")),
      None
    ) shouldBe "from 1.2.0 to 1.2.3"

    NewPullRequestData.fromTo(
      Update.Group(GroupId("com.example"), Nel.of("foo", "bar"), "1.2.0", Nel.of("1.2.3")),
      Some("http://example.com/compare/v1.2.0...v1.2.3")
    ) shouldBe
      "[from 1.2.0 to 1.2.3](http://example.com/compare/v1.2.0...v1.2.3)"
  }

  test("links to release notes/changelog") {
    NewPullRequestData.releaseNote(None) shouldBe None

    NewPullRequestData.releaseNote(Some("https://github.com/foo/foo/CHANGELOG.rst")) shouldBe Some(
      "[Release Notes/Changelog](https://github.com/foo/foo/CHANGELOG.rst)"
    )
  }

  test("showing artifacts with URL in Markdown format") {
    NewPullRequestData.artifactsWithOptionalUrl(
      Update.Single(GroupId("com.example"), "foo", "1.2.0", Nel.of("1.2.3")),
      Map("foo" -> "https://github.com/foo/foo")
    ) shouldBe "[com.example:foo](https://github.com/foo/foo)"

    NewPullRequestData.artifactsWithOptionalUrl(
      Update.Group(GroupId("com.example"), Nel.of("foo", "bar"), "1.2.0", Nel.of("1.2.3")),
      Map("foo" -> "https://github.com/foo/foo", "bar" -> "https://github.com/bar/bar")
    ) shouldBe
      """
        |* [com.example:foo](https://github.com/foo/foo)
        |* [com.example:bar](https://github.com/bar/bar)
        |
        |""".stripMargin
  }

  test("migrationNote: when no migrations") {
    val update = Update.Single(GroupId("com.example"), "foo", "0.6.0", Nel.of("0.7.0"))
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(update)

    label shouldBe None
    appliedMigrations shouldBe None
  }

  test("migrationNote: when artifact has migrations") {
    val update = Update.Single(GroupId("com.spotify"), "scio-core", "0.6.0", Nel.of("0.7.0"))
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(update)

    label shouldBe Some("scalafix-migrations")
    appliedMigrations.getOrElse("") shouldBe
      """<details>
        |<summary>Applied Migrations</summary>
        |
        |* github:spotify/scio/FixAvroIO?sha=v0.7.4
        |* github:spotify/scio/AddMissingImports?sha=v0.7.4
        |* github:spotify/scio/RewriteSysProp?sha=v0.7.4
        |* github:spotify/scio/BQClientRefactoring?sha=v0.7.4
        |</details>
      """.stripMargin.trim
  }
}
