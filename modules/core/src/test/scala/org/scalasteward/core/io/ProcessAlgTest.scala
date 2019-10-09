package org.scalasteward.core.io

import better.files.File
import cats.effect.{Blocker, IO}
import java.util.concurrent.Executors
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.io.ProcessAlgTest.ioProcessAlg
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProcessAlgTest extends AnyFunSuite with Matchers {
  test("exec echo") {
    ioProcessAlg
      .exec(Nel.of("echo", "-n", "hello"), File.currentWorkingDirectory)
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
      commands = Vector(
        List("TEST_VAR=GREAT", "ANOTHER_TEST_VAR=ALSO_GREAT", File.temp.toString, "echo", "hello")
      )
    )
  }

  test("execSandboxed echo") {
    val state = processAlg
      .execSandboxed(Nel.of("echo", "hello"), File.temp)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          File.temp.toString,
          "firejail",
          s"--whitelist=${File.temp}",
          "echo",
          "hello"
        )
      )
    )
  }
}

object ProcessAlgTest {
  val blocker: Blocker = Blocker.liftExecutorService(Executors.newCachedThreadPool())
  implicit val ioProcessAlg: ProcessAlg[IO] = ProcessAlg.create[IO](blocker)
}
