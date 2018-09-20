/*
 * Copyright 2018 scala-steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.timepit.scalasteward

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import cats.effect.IO
import eu.timepit.scalasteward.model.Update

import scala.concurrent.duration.FiniteDuration
import cats.implicits._

object log {
  val now: IO[String] =
    IO(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))

  def printInfo(msg: String): IO[Unit] =
    printImpl("I", msg)

  def printWarning(msg: String): IO[Unit] =
    printImpl("W", msg)

  def printTimed[A](msg: FiniteDuration => String)(fa: IO[A]): IO[A] =
    io.timed(fa).flatMap {
      case (a, duration) => printInfo(msg(duration)) >> IO.pure(a)
    }

  def printTotalTime[A](fa: IO[A]): IO[A] =
    printTimed(duration => s"Total time: ${util.show(duration)}")(fa)

  def printImpl(level: String, msg: String): IO[Unit] =
    now.flatMap(dt => IO(println(s"[$dt] $level: $msg")))

  def printUpdates(updates: List[Update]): IO[Unit] = {
    val list = updates.map(u => "  " + u.show).mkString("\n")
    val msg = updates.size match {
      case 0 => "Found 0 updates"
      case 1 => s"Found 1 update:\n$list"
      case n => s"Found $n updates:\n$list"
    }
    log.printInfo(msg)
  }
}
