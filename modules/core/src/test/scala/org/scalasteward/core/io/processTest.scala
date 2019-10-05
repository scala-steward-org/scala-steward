package org.scalasteward.core.io

import cats.effect.IO
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.io.ProcessAlgTest.blocker
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class processTest extends AnyFunSuite with Matchers {
  def slurp1(cmd: Nel[String]): IO[List[String]] =
    process.slurp[IO](cmd, None, Map.empty, 1.minute, _ => IO.unit, blocker)

  def slurp2(cmd: Nel[String], timeout: FiniteDuration): IO[List[String]] =
    process.slurp[IO](cmd, None, Map.empty, timeout, _ => IO.unit, blocker)

  test("echo hello") {
    slurp1(Nel.of("echo", "-n", "hello")).unsafeRunSync() shouldBe List("hello")
  }

  test("echo hello world") {
    slurp1(Nel.of("echo", "-n", "hello\nworld")).unsafeRunSync() shouldBe List("hello", "world")
  }

  test("ls") {
    slurp1(Nel.of("ls")).attempt.unsafeRunSync().isRight shouldBe true
  }

  test("ls --foo") {
    slurp1(Nel.of("ls", "--foo")).attempt.unsafeRunSync().isLeft shouldBe true
  }

  test("sleep 1: ok") {
    slurp2(Nel.of("sleep", "1"), 2.seconds).unsafeRunSync() shouldBe List()
  }

  test("sleep 1: fail") {
    slurp2(Nel.of("sleep", "1"), 500.milliseconds).attempt.unsafeRunSync().isLeft shouldBe true
  }
}
