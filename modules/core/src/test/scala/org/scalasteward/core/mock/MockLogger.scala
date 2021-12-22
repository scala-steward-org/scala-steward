package org.scalasteward.core.mock

import cats.data.Kleisli
import org.typelevel.log4cats.Logger

class MockLogger extends Logger[MockEff] {
  override def error(t: Throwable)(message: => String): MockEff[Unit] =
    impl(Some(t), message)

  override def warn(t: Throwable)(message: => String): MockEff[Unit] =
    impl(Some(t), message)

  override def info(t: Throwable)(message: => String): MockEff[Unit] =
    impl(Some(t), message)

  override def debug(t: Throwable)(message: => String): MockEff[Unit] =
    impl(Some(t), message)

  override def trace(t: Throwable)(message: => String): MockEff[Unit] =
    impl(Some(t), message)

  override def error(message: => String): MockEff[Unit] =
    impl(None, message)

  override def warn(message: => String): MockEff[Unit] =
    impl(None, message)

  override def info(message: => String): MockEff[Unit] =
    impl(None, message)

  override def debug(message: => String): MockEff[Unit] =
    impl(None, message)

  override def trace(message: => String): MockEff[Unit] =
    impl(None, message)

  def impl(maybeThrowable: Option[Throwable], message: String): MockEff[Unit] =
    Kleisli(_.update(_.log(maybeThrowable, message)))
}
