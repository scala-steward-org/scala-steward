/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.util

import cats.Order
import io.circe.{Decoder, Encoder}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

final case class Timestamp(millis: Long) {
  def +(finiteDuration: FiniteDuration): Timestamp =
    Timestamp(millis + finiteDuration.toMillis)

  def toLocalDateTime: LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

  def until(that: Timestamp): FiniteDuration =
    FiniteDuration(that.millis - millis, TimeUnit.MILLISECONDS)
}

object Timestamp {
  def fromLocalDateTime(ldt: LocalDateTime): Timestamp =
    Timestamp(ldt.toInstant(ZoneOffset.UTC).toEpochMilli)

  implicit val timestampDecoder: Decoder[Timestamp] =
    Decoder[Long].map(Timestamp.apply)

  implicit val timestampEncoder: Encoder[Timestamp] =
    Encoder[Long].contramap(_.millis)

  implicit val timestampOrder: Order[Timestamp] =
    Order.by(_.millis)
}
