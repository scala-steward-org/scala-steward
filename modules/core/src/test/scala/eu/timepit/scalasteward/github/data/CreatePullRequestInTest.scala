package eu.timepit.scalasteward.github.data

import cats.data.NonEmptyList
import eu.timepit.scalasteward.git.Branch
import eu.timepit.scalasteward.github.data.CreatePullRequestIn.bodyOf
import eu.timepit.scalasteward.model.Update
import io.circe.syntax._
import org.scalatest.{FunSuite, Matchers}

class CreatePullRequestInTest extends FunSuite with Matchers {
  test("encode") {
    CreatePullRequestIn(
      "Update logback-classic to 1.2.3",
      bodyOf(Update.Single("ch.qos.logback", "logback-classic", "1.2.0", NonEmptyList.of("1.2.3"))),
      "scala-steward:update/logback-classic-1.2.3",
      Branch("master")
    ).asJson.spaces2 shouldBe
      """|{
         |  "title" : "Update logback-classic to 1.2.3",
         |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention @scala-steward in the comments below.\n\nHave a nice day!",
         |  "head" : "scala-steward:update/logback-classic-1.2.3",
         |  "base" : "master"
         |}
         |""".stripMargin.trim
  }
}
