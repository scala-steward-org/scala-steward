package org.scalasteward.core.io

import cats.effect.IO
import munit.FunSuite
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.io.ProcessAlgTest.blocker
import org.scalasteward.core.io.process.Args
import org.scalasteward.core.util.Nel
import scala.concurrent.duration._

class processTest extends FunSuite {
  def slurp1(cmd: Nel[String]): IO[List[String]] =
    process.slurp[IO](Args(cmd), 1.minute, 8192, _ => IO.unit, blocker)

  def slurp2(cmd: Nel[String], timeout: FiniteDuration): IO[List[String]] =
    process.slurp[IO](Args(cmd), timeout, 8192, _ => IO.unit, blocker)

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
    assert(slurp2(Nel.of("sleep", "1"), 500.milliseconds).attempt.unsafeRunSync().isLeft)
  }
}
