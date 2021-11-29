package org.scalasteward.core.io

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.io.process.Args
import org.scalasteward.core.util.{DateTimeAlg, Nel}
import scala.concurrent.duration._

class processTest extends FunSuite {
  def slurp1(cmd: Nel[String]): IO[List[String]] =
    process.slurp[IO](Args(cmd), 1.minute, 8192, _ => IO.unit)

  def slurp2(cmd: Nel[String], timeout: FiniteDuration): IO[List[String]] =
    process.slurp[IO](Args(cmd), timeout, 8192, _ => IO.unit)

  test("echo hello") {
    assertEquals(slurp1(Nel.of("echo", "-n", "hello")).unsafeRunSync(), List("hello"))
  }

  test("echo hello world") {
    assertEquals(
      slurp1(Nel.of("echo", "-n", "hello\nworld")).unsafeRunSync(),
      List("hello", "world")
    )
  }

  test("ls") {
    assert(slurp1(Nel.of("ls")).attempt.unsafeRunSync().isRight)
  }

  test("ls --foo") {
    assert(slurp1(Nel.of("ls", "--foo")).attempt.unsafeRunSync().isLeft)
  }

  test("sleep 1: ok") {
    assertEquals(slurp2(Nel.of("sleep", "1"), 2.seconds).unsafeRunSync(), List())
  }

  test("sleep 1: fail") {
    val timeout = 500.milliseconds
    val sleep = timeout * 2
    val p = slurp2(Nel.of("sleep", sleep.toSeconds.toInt.toString), timeout).attempt
    val (res, fd) = DateTimeAlg.create[IO].timed(p).unsafeRunSync()

    assert(res.isLeft)
    assert(clue(fd) > timeout)
    assert(clue(fd) < sleep)
  }
}
