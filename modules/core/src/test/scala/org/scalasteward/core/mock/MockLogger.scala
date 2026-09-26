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
