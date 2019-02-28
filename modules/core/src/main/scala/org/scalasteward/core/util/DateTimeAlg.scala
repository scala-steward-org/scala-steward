package org.scalasteward.core.util

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait DateTimeAlg[F[_]] {
  def currentTimeMillis: F[Long]

  def timed[A](fa: F[A])(implicit F: Monad[F]): F[(A, FiniteDuration)] =
    for {
      start <- currentTimeMillis
      a <- fa
      end <- currentTimeMillis
      duration = FiniteDuration(end - start, TimeUnit.MILLISECONDS)
    } yield (a, duration)
}

object DateTimeAlg {
  def create[F[_]](implicit F: Sync[F]): DateTimeAlg[F] =
    new DateTimeAlg[F] {
      override def currentTimeMillis: F[Long] =
        F.delay(System.currentTimeMillis())
    }
}
