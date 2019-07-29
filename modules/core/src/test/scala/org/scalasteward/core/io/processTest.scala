package org.scalasteward.core.io

import cats.effect.{ContextShift, IO, Timer}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class processTest extends FunSuite with Matchers {
  implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timerIO: Timer[IO] = IO.timer(ExecutionContext.global)

  def slurp1(cmd: Nel[String]): IO[List[String]] =
    process.slurp[IO](cmd, 1.minute, _ => IO.unit, _ => IO.unit)

  def slurp2(cmd: Nel[String], timeout: FiniteDuration): IO[List[String]] =
    process.slurp[IO](cmd, timeout, _ => IO.unit, _ => IO.unit)

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
