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

  test("respect the disableSandbox setting") {
    val cfg = config.copy(disableSandbox = true)
    val processAlg = new MockProcessAlg()(cfg)

    val state = processAlg
      .execSandboxed(Nel.of("echo", "hello"), File.temp)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(List(File.temp.toString, "echo", "hello")),
      extraEnv = Vector(List(("TEST_VAR", "GREAT"), ("ANOTHER_TEST_VAR", "ALSO_GREAT")))
    )
  }

  test("execSandboxed echo") {
    val state = processAlg
      .execSandboxed(Nel.of("echo", "hello"), File.temp)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(File.temp.toString, "firejail", s"--whitelist=${File.temp}", "echo", "hello")
      ),
      extraEnv = Vector(
        List(("TEST_VAR", "GREAT"), ("ANOTHER_TEST_VAR", "ALSO_GREAT"))
      )
    )
  }
}
