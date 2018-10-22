package eu.timepit.scalasteward.github.data

import cats.data.NonEmptyList
import eu.timepit.scalasteward.git.Branch
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.nurture.UpdateData
import io.circe.syntax._
import org.scalatest.{FunSuite, Matchers}

class NewPullRequestDataTest extends FunSuite with Matchers {
  test("asJson") {
    val data = UpdateData(
      Repo("foo", "bar"),
      Update.Single("ch.qos.logback", "logback-classic", "1.2.0", NonEmptyList.of("1.2.3")),
      Branch("master"),
      Branch("update/logback-classic-1.2.3")
    )
    NewPullRequestData.from(data, "scala-steward").asJson.spaces2 shouldBe
      """|{
         |  "title" : "Update logback-classic to 1.2.3",
         |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention @scala-steward in the comments below.\n\nHave a nice day!",
         |  "head" : "scala-steward:update/logback-classic-1.2.3",
         |  "base" : "master"
         |}
         |""".stripMargin.trim
  }
}
