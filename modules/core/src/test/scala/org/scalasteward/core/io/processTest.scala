package org.scalasteward.core.io

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.io.process.*
import org.scalasteward.core.util.{DateTimeAlg, Nel}
import scala.concurrent.duration.*

class processTest extends FunSuite {
  def slurp1(cmd: Nel[String]): IO[List[String]] =
    slurp[IO](Args(cmd), 1.minute, 8192, _ => IO.unit)

  def slurp2(cmd: Nel[String], timeout: FiniteDuration): IO[List[String]] =
    slurp[IO](Args(cmd), timeout, 8192, _ => IO.unit)

  def slurp3(
      cmd: Nel[String],
      maxBufferSize: Int,
      slurpOptions: SlurpOptions
  ): IO[List[String]] =
    slurp[IO](Args(cmd, slurpOptions = slurpOptions), 1.minute, maxBufferSize, _ => IO.unit)

  test("echo: ok, one line") {
    assertEquals(slurp1(Nel.of("echo", "-n", "hello")).unsafeRunSync(), List("hello"))
  }

  test("echo: ok, two lines") {
    val obtained = slurp1(Nel.of("echo", "-n", "hello\nworld")).unsafeRunSync()
    assertEquals(obtained, List("hello", "world"))
  }

  test("echo: ok, buffer size exceeded") {
    val obtained =
      slurp3(Nel.of("echo", "-n", "1\n2\n3\n4\n5\n6"), 4, SlurpOptions.ignoreBufferOverflow)
        .unsafeRunSync()
    assertEquals(obtained, List("3", "4", "5", "6"))
  }

  test("echo: fail, buffer size exceeded") {
    val Left(t) = slurp3(Nel.of("echo", "-n", "1\n2\n3\n4\n5\n6"), 4, Set.empty).attempt
      .unsafeRunSync(): @unchecked
    assert(clue(t).isInstanceOf[ProcessBufferOverflowException])
  }

  test("echo: fail, line length > buffer size") {
    val Left(t) =
      slurp3(Nel.of("echo", "-n", "123456"), 4, Set.empty).attempt.unsafeRunSync(): @unchecked
    assert(clue(t).isInstanceOf[ProcessLineTooLongException])
  }

  test("ls: ok") {
    assert(slurp1(Nel.of("ls")).attempt.unsafeRunSync().isRight)
  }

  test("ls: fail, non-zero exit code") {
    val Left(t) = slurp1(Nel.of("ls", "--foo")).attempt.unsafeRunSync(): @unchecked
    assert(clue(t).isInstanceOf[ProcessFailedException])
  }

  test("sleep 1: ok") {
    assertEquals(slurp2(Nel.of("sleep", "1"), 2.seconds).unsafeRunSync(), List())
  }

  test("sleep 1: fail, timeout") {
    val timeout = 500.milliseconds
    val sleep = timeout * 2
    val p = slurp2(Nel.of("sleep", sleep.toSeconds.toInt.toString), timeout).attempt
    val (Left(t), fd) = DateTimeAlg.create[IO].timed(p).unsafeRunSync(): @unchecked

    assert(clue(t).isInstanceOf[ProcessTimedOutException])
    assert(clue(fd) > timeout)
    assert(clue(fd) < sleep)
  }

  test("do not wait for user input") {
    // This would time out if standard input is not closed.
    val obtained = slurp2(Nel.of("dd", "count=1"), 1.second).attempt.unsafeRunSync()
    assert(clue(obtained).isRight)
  }
}
