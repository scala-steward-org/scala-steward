package org.scalasteward.core.util

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import scala.concurrent.duration.FiniteDuration

class LogAlg[F[_]](
    implicit
    dateTimeAlg: DateTimeAlg[F],
    logger: Logger[F],
    F: MonadThrowable[F]
) {
  def attemptLog_[A](message: String)(fa: F[A]): F[Unit] =
    logger.info(message) >> fa.attempt.flatMap {
      case Left(t)  => logger.error(t)(s"$message failed")
      case Right(_) => F.unit
    }

  def infoTimed[A](msg: FiniteDuration => String)(fa: F[A]): F[A] =
    dateTimeAlg.timed(fa).flatMap {
      case (a, duration) => logger.info(msg(duration)) >> F.pure(a)
    }

  def infoTotalTime[A](label: String)(fa: F[A]): F[A] = {
    val label1 = if (label.nonEmpty) s" $label:" else ""
    infoTimed { duration =>
      string.lineLeftRight(s"Total time:$label1 ${dateTime.showDuration(duration)}")
    }(fa)
  }
}
