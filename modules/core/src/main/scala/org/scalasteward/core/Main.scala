/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core

import cats.effect.{ExitCode, IO, IOApp}
import org.scalasteward.core.application.{Cli, Context}

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Cli.parseArgs(args) match {
      case Cli.ParseResult.Success(args) => Context.step0[IO](args).use(_.stewardAlg.runF)
      case Cli.ParseResult.Help(help)    => IO(Console.out.println(help)).as(ExitCode.Success)
      case Cli.ParseResult.Error(error)  => IO(Console.err.println(error)).as(ExitCode.Error)
    }
}
