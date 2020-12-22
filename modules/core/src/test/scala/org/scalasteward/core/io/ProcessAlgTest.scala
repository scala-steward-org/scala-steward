package org.scalasteward.core.io

import better.files.File
import cats.effect.{Blocker, IO}
import java.util.concurrent.Executors
import munit.FunSuite
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.application.Config.{ProcessCfg, SandboxCfg}
import org.scalasteward.core.io.ProcessAlgTest.ioProcessAlg
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import scala.concurrent.duration.Duration

class ProcessAlgTest extends FunSuite {
  test("exec: echo") {
    val obtained = ioProcessAlg
      .exec(Nel.of("echo", "-n", "hello"), File.currentWorkingDirectory)
      .unsafeRunSync()
    assertEquals(obtained, List("hello"))
  }

  test("exec: ls --foo") {
    assert(
      ioProcessAlg
        .exec(Nel.of("ls", "--foo"), File.currentWorkingDirectory)
        .attempt
        .map(_.isLeft)
        .unsafeRunSync()
    )
  }

  test("execSandboxed: echo with enableSandbox = false") {
    val cfg = ProcessCfg(Nil, Duration.Zero, SandboxCfg(Nil, Nil, enableSandbox = false), 8192)
    val state = MockProcessAlg
      .create(cfg)
      .execSandboxed(Nel.of("echo", "hello"), File.temp)
      .runS(MockState.empty)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      commands = Vector(
        List(File.temp.toString, "echo", "hello")
      )
    )

    assertEquals(state, expected)
  }

  test("execSandboxed: echo with enableSandbox = true") {
    val cfg = ProcessCfg(Nil, Duration.Zero, SandboxCfg(Nil, Nil, enableSandbox = true), 8192)
    val state = MockProcessAlg
      .create(cfg)
      .execSandboxed(Nel.of("echo", "hello"), File.temp)
      .runS(MockState.empty)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      commands = Vector(
        List(
          File.temp.toString,
          "firejail",
          "--quiet",
          s"--whitelist=${File.temp}",
          "echo",
          "hello"
        )
      )
    )

    assertEquals(state, expected)
  }
}

object ProcessAlgTest {
  val blocker: Blocker = Blocker.liftExecutorService(Executors.newCachedThreadPool())
  implicit val ioProcessAlg: ProcessAlg[IO] = ProcessAlg.create[IO](blocker, config.processCfg)
}
