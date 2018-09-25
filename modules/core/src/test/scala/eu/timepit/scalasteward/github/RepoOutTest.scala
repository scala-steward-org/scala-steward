package eu.timepit.scalasteward.github

import eu.timepit.scalasteward.model.Branch
import io.circe.parser
import org.scalatest.{FunSuite, Matchers}
import scala.io.Source

class RepoOutTest extends FunSuite with Matchers {
  test("decode[RepoOut]") {
    val input = Source.fromResource("create-fork.json").mkString
    parser.decode[RepoOut](input) shouldBe
      Right(
        RepoOut(
          "base.g8-1",
          UserOut("scala-steward"),
          Some(RepoOut("base.g8", UserOut("ChristopherDavenport"), None, Branch("master"))),
          Branch("master")
        )
      )
  }
}
