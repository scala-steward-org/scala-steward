package org.scalasteward.core.io

import better.files.File
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestInstances.*
import org.scalasteward.core.application.Config.{ProcessCfg, SandboxCfg}
import org.scalasteward.core.io.ProcessAlgTest.ioProcessAlg
import org.scalasteward.core.mock.MockConfig.{config, mockRoot}
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEffOps, MockState}
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
      .execSandboxed(Nel.of("echo", "hello"), mockRoot)
      .runS(MockState.empty)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      trace = Vector(Cmd(mockRoot.toString, "echo", "hello"))
    )

    assertEquals(state, expected)
  }

  test("execSandboxed: echo with enableSandbox = true") {
    val cfg = ProcessCfg(Nil, Duration.Zero, SandboxCfg(Nil, Nil, enableSandbox = true), 8192)
    val state = MockProcessAlg
      .create(cfg)
      .execSandboxed(Nel.of("echo", "hello"), mockRoot)
      .runS(MockState.empty)
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd(mockRoot.toString, "firejail", "--quiet", s"--whitelist=$mockRoot", "echo", "hello")
      )
    )

    assertEquals(state, expected)
  }
}

object ProcessAlgTest {
  implicit val ioProcessAlg: ProcessAlg[IO] = ProcessAlg.create[IO](config.processCfg)
}
