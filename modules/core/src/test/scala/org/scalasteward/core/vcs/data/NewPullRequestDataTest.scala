package org.scalasteward.core.vcs.data

import io.circe.syntax._
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.model.Update
import org.scalasteward.core.nurture.UpdateData
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class NewPullRequestDataTest extends FunSuite with Matchers {
  test("asJson") {
    val data = UpdateData(
      Repo("foo", "bar"),
      Update.Single("ch.qos.logback", "logback-classic", "1.2.0", Nel.of("1.2.3")),
      Branch("master"),
      Sha1(Sha1.HexString("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
      Branch("update/logback-classic-1.2.3")
    )
    NewPullRequestData.from(data, "foo", "scala-steward").asJson.spaces2 shouldBe
      """|{
         |  "title" : "Update logback-classic to 1.2.3",
         |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention @scala-steward in the comments below.\n\nHave a nice day!\n\n<details>\n<summary>Ignore future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [{ groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" }]\n```\n</details>\n\nlabels: semver-patch",
         |  "head" : "foo:update/logback-classic-1.2.3",
         |  "base" : "master"
         |}
         |""".stripMargin.trim
  }
}
