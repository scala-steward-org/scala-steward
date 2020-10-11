package org.scalasteward.core.io

import better.files.File
import cats.effect.{Blocker, IO}
import java.util.concurrent.Executors
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.application.Config.{ProcessCfg, SandboxCfg}
import org.scalasteward.core.io.ProcessAlgTest.ioProcessAlg
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.Duration

class ProcessAlgTest extends AnyFunSuite with Matchers {
  test("exec: echo") {
    ioProcessAlg
      .exec(Nel.of("echo", "-n", "hello"), File.currentWorkingDirectory)
      .unsafeRunSync() shouldBe List("hello")
  }

  test("exec: ls --foo") {
    ioProcessAlg
      .exec(Nel.of("ls", "--foo"), File.currentWorkingDirectory)
      .attempt
      .map(_.isLeft)
      .unsafeRunSync()
  }

  test("execSandboxed: echo with disableSandbox = true") {
    val cfg = ProcessCfg(Nil, Duration.Zero, SandboxCfg(Nil, Nil, disableSandbox = true))
    val state = new MockProcessAlg(cfg)
      .execSandboxed(Nel.of("echo", "hello"), File.temp)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(File.temp.toString, "echo", "hello")
      )
    )
  }

  test("execSandboxed: echo with disableSandbox = false") {
    val cfg = ProcessCfg(Nil, Duration.Zero, SandboxCfg(Nil, Nil, disableSandbox = false))
    val state = new MockProcessAlg(cfg)
      .execSandboxed(Nel.of("echo", "hello"), File.temp)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(File.temp.toString, "firejail", s"--whitelist=${File.temp}", "echo", "hello")
      )
    )
  }
}

object ProcessAlgTest {
  val blocker: Blocker = Blocker.liftExecutorService(Executors.newCachedThreadPool())
  implicit val ioProcessAlg: ProcessAlg[IO] = ProcessAlg.create[IO](blocker, config.processCfg)
}
