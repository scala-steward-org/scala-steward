package eu.timepit.scalasteward.github

import io.circe.syntax._
import org.scalatest.{FunSuite, Matchers}

class CreatePullRequestInTest extends FunSuite with Matchers {
  test("asJson") {
    CreatePullRequestIn(
      "Update logback-classic to 1.2.3",
      """|Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.
         |
         |Have a nice day!
         |""".stripMargin.trim,
      "scala-steward:update/logback-classic-1.2.3",
      "master"
    ).asJson.spaces2 shouldBe
      """|{
         |  "title" : "Update logback-classic to 1.2.3",
         |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\nHave a nice day!",
         |  "head" : "scala-steward:update/logback-classic-1.2.3",
         |  "base" : "master"
         |}
         |""".stripMargin.trim
  }
}
