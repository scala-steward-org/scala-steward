package eu.timepit.scalasteward.github.data

import eu.timepit.scalasteward.git.{Branch, Sha1}
import io.circe.parser
import org.scalatest.{FunSuite, Matchers}
import scala.io.Source

class BranchOutTest extends FunSuite with Matchers {
  test("decode") {
    val input = Source.fromResource("get-branch.json").mkString
    parser.decode[BranchOut](input) shouldBe
      Right(
        BranchOut(Branch("master"), CommitOut(Sha1("7fd1a60b01f91b314f59955a4e4d4e80d8edf11d")))
      )
  }
}
