package eu.timepit.scalasteward.util

import cats.data.State
import eu.timepit.scalasteward.MockState.MockEnv
import io.chrisdavenport.log4cats.Logger

class MockLogger extends Logger[MockEnv] {
  override def error(t: Throwable)(message: => String): MockEnv[Unit] =
    impl(Some(t), message)

  override def warn(t: Throwable)(message: => String): MockEnv[Unit] =
    impl(Some(t), message)

  override def info(t: Throwable)(message: => String): MockEnv[Unit] =
    impl(Some(t), message)

  override def debug(t: Throwable)(message: => String): MockEnv[Unit] =
    impl(Some(t), message)

  override def trace(t: Throwable)(message: => String): MockEnv[Unit] =
    impl(Some(t), message)

  override def error(message: => String): MockEnv[Unit] =
    impl(None, message)

  override def warn(message: => String): MockEnv[Unit] =
    impl(None, message)

  override def info(message: => String): MockEnv[Unit] =
    impl(None, message)

  override def debug(message: => String): MockEnv[Unit] =
    impl(None, message)

  override def trace(message: => String): MockEnv[Unit] =
    impl(None, message)

  def impl(maybeThrowable: Option[Throwable], message: String): MockEnv[Unit] =
    State.modify(_.log(maybeThrowable, message))
}
