package eu.timepit.scalasteward.io

import better.files.File
import cats.effect.IO
import org.scalatest.{FunSuite, Matchers}

class ProcessAlgTest extends FunSuite with Matchers {
  val processAlg: ProcessAlg[IO] =
    ProcessAlg.create[IO]

  test("exec echo") {
    processAlg
      .exec(List("echo", "hello"), File.currentWorkingDirectory)
      .unsafeRunSync() shouldBe List("hello")
  }

  test("exec false") {
    processAlg.exec(List("false"), File.currentWorkingDirectory).attempt.unsafeRunSync().isLeft
  }
}
