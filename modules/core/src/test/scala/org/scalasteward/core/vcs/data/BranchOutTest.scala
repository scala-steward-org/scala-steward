package org.scalasteward.core.vcs.data

import io.circe.parser
import munit.FunSuite
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import scala.io.Source

class BranchOutTest extends FunSuite {
  test("decode") {
    val input = Source.fromResource("get-branch.json").mkString
    val expected = Right(
      BranchOut(
        Branch("master"),
        CommitOut(Sha1(HexString.unsafeFrom("7fd1a60b01f91b314f59955a4e4d4e80d8edf11d")))
      )
    )
    assertEquals(parser.decode[BranchOut](input), expected)
  }
}
