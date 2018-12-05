package org.scalasteward.core.io

import better.files.File
import cats.effect.IO
import org.scalasteward.core.application.{Config, ConfigTest}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class ProcessAlgTest extends FunSuite with Matchers {
  implicit val config: Config = ConfigTest.dummyConfig
  val processAlg: ProcessAlg[IO] = ProcessAlg.create[IO]

  test("exec echo") {
    processAlg
      .exec(Nel.of("echo", "hello"), File.currentWorkingDirectory)
      .unsafeRunSync() shouldBe List("hello")
  }

  test("exec false") {
    processAlg
      .exec(Nel.of("ls", "--foo"), File.currentWorkingDirectory)
      .attempt
      .map(_.isLeft)
      .unsafeRunSync()
  }
}
