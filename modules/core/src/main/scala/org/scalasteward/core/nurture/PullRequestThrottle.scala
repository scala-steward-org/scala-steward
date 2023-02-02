/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.core.nurture

import cats.Monad
import cats.effect.Async
import cats.syntax.all._
import org.scalasteward.core.application.Config.PullRequestThrottleCfg
import org.scalasteward.core.util.{dateTime, DateTimeAlg, SimpleTimer}
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.FiniteDuration

final class PullRequestThrottle[F[_]](config: PullRequestThrottleCfg, timer: SimpleTimer[F])(
    implicit
    logger: Logger[F],
    F: Monad[F]
) {
  def hit: F[Unit] =
    config.skipFor.orElse(config.waitFor).fold(F.unit)(timer.start)

  def throttle(f: F[Unit]): F[Unit] =
    timer.remaining.flatMap {
      case Some(remaining) if config.skipFor.isDefined =>
        log("skipping", remaining)
      case Some(remaining) if config.waitFor.isDefined =>
        log("waiting", remaining) >> timer.await >> f
      case _ => f
    }

  private def log(action: String, remaining: FiniteDuration): F[Unit] =
    logger.info(s"PR throttle is active: $action for ${dateTime.showDuration(remaining)}")
}

object PullRequestThrottle {
  def create[F[_]](config: PullRequestThrottleCfg)(implicit
      dateTimeAlg: DateTimeAlg[F],
      logger: Logger[F],
      F: Async[F]
  ): F[PullRequestThrottle[F]] =
    SimpleTimer.create.map(new PullRequestThrottle(config, _))
}
