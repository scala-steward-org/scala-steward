package org.scalasteward.core.io

import org.scalasteward.core.MockState
import org.scalatest.{FunSuite, Matchers}

class FileAlgTest extends FunSuite with Matchers {
  val mockFileAlg: MockFileAlg = new MockFileAlg

  test("editFile: nonexistent file") {
    val (state, edited) = (for {
      home <- mockFileAlg.home
      edited <- mockFileAlg.editFile(home / "does-not-exists.txt", Some.apply)
    } yield edited).run(MockState.empty).value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", "/tmp/steward/does-not-exists.txt")
      )
    )
    edited shouldBe false
  }
}
