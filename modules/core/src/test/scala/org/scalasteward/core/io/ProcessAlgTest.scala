package org.scalasteward.core.io

import better.files.File
import cats.effect.IO
import org.scalasteward.core.MockState
import org.scalasteward.core.application.{Config, ConfigTest}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class ProcessAlgTest extends FunSuite with Matchers {
  implicit val config: Config = ConfigTest.dummyConfig
  val ioProcessAlg: ProcessAlg[IO] = ProcessAlg.create[IO]
  val mockProcessAlg: MockProcessAlg = new MockProcessAlg

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
    val state = mockProcessAlg
      .execSandboxed(Nel.of("echo", "hello"), File.root / "tmp")
      .runS(MockState.empty)
      .value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("firejail", "--whitelist=/tmp", "echo", "hello")
      )
    )
  }
}
