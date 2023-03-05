package org.scalasteward.core.util

import cats.effect.IO
import munit.CatsEffectSuite
import scala.concurrent.duration._

class SimpleTimerTest extends CatsEffectSuite {
  implicit private val dateTimeAlg: DateTimeAlg[IO] = DateTimeAlg.create[IO]

  test("'expired' is true without 'start'") {
    for {
      t <- SimpleTimer.create[IO]
      expired <- t.expired
      _ = assert(expired)
    } yield ()
  }

  test("'expired' is true after 'start' if 'duration' is zero") {
    for {
      t <- SimpleTimer.create[IO]
      _ <- t.start(0.seconds)
      expired <- t.expired
      _ = assert(expired)
    } yield ()
  }

  test("'expired' is false after 'start' if 'duration' is non-zero") {
    for {
      t <- SimpleTimer.create[IO]
      _ <- t.start(1.second)
      expired <- t.expired
      _ = assert(!expired)
    } yield ()
  }

  test("'expired' is true after 'start' if 'duration' elapsed") {
    for {
      t <- SimpleTimer.create[IO]
      d = 50.millis
      _ <- t.start(d)
      _ <- IO.sleep(d)
      expired <- t.expired
      _ = assert(expired)
    } yield ()
  }

  test("'await' sleeps for 'duration' after 'start'") {
    for {
      t <- SimpleTimer.create[IO]
      d = 50.millis
      res <- dateTimeAlg.timed(t.start(d) >> t.await >> t.expired)
      (expired, duration) = res
      _ = assert(expired)
      _ = assert(clue(duration) >= d)
    } yield ()
  }
}
