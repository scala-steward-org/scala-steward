package org.scalasteward.core.vcs.data

import io.circe.syntax._
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.model.Update
import org.scalasteward.core.nurture.UpdateData
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class NewPullRequestDataTest extends FunSuite with Matchers {
  test("asJson") {
    val data = UpdateData(
      Repo("foo", "bar"),
      Repo("scala-steward", "bar"),
      RepoConfig(),
      Update.Single("ch.qos.logback", "logback-classic", "1.2.0", Nel.of("1.2.3")),
      Branch("master"),
      Sha1(Sha1.HexString("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
      Branch("update/logback-classic-1.2.3")
    )
    NewPullRequestData
      .from(data, "scala-steward:update/logback-classic-1.2.3", "scala-steward")
      .asJson
      .spaces2 shouldBe
      """|{
         |  "title" : "Update logback-classic to 1.2.3",
         |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention @scala-steward in the comments below.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Ignore future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [{ groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" }]\n```\n</details>\n\nlabels: semver-patch",
         |  "head" : "scala-steward:update/logback-classic-1.2.3",
         |  "base" : "master"
         |}
         |""".stripMargin.trim
  }

  test("migrationNote: when no migrations") {
    val update = Update.Single("com.example", "foo", "0.6.0", Nel.of("0.7.0"))
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(update)

    label shouldBe None
    appliedMigrations shouldBe None
  }

  test("migrationNote: when artifact has migrations") {
    val update = Update.Single("com.spotify", "scio-core", "0.6.0", Nel.of("0.7.0"))
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
