package org.scalasteward.core.io

import better.files.File
import cats.effect.IO
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class ProcessAlgTest extends FunSuite with Matchers {
  val ioProcessAlg: ProcessAlg[IO] = ProcessAlg.create[IO]

  test("exec echo") {
    ioProcessAlg
      .exec(Nel.of("echo", "hello"), File.currentWorkingDirectory)
      .unsafeRunSync() shouldBe List("hello")
  }

  test("exec false") {
    ioProcessAlg
      .exec(Nel.of("ls", "--foo"), File.currentWorkingDirectory)
      .attempt
      .map(_.isLeft)
      .unsafeRunSync()
  }

  test("execSandboxed echo") {
    val state = processAlg
      .execSandboxed(Nel.of("echo", "hello"), File.root / "tmp")
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("firejail", "--whitelist=/tmp", "echo", "hello")
      )
    )
  }
}
