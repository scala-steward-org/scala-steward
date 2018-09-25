package eu.timepit.scalasteward.github.data

import eu.timepit.scalasteward.model.Branch
import io.circe.parser
import org.scalatest.{FunSuite, Matchers}
import scala.io.Source

class RepoOutTest extends FunSuite with Matchers {
  test("decode") {
    val input = Source.fromResource("create-fork.json").mkString
    parser.decode[RepoOut](input) shouldBe
      Right(
        RepoOut(
          "base.g8-1",
          UserOut("scala-steward"),
          Some(
            RepoOut(
              "base.g8",
              UserOut("ChristopherDavenport"),
              None,
              "https://github.com/ChristopherDavenport/base.g8.git",
              Branch("master")
            )
          ),
          "https://github.com/scala-steward/base.g8-1.git",
          Branch("master")
        )
      )
  }
}
