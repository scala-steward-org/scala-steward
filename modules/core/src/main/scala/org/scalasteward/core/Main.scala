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

package org.scalasteward.core

import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp}
import org.scalasteward.core.application.{Cli, Context, ValidateRepoConfigContext}

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Cli.parseArgs(args) match {
      case Cli.ParseResult.Success(Cli.Usage.Regular(config)) =>
        Context.step0[IO](config).use(_.stewardAlg.runF)
      case Cli.ParseResult.Success(Cli.Usage.ValidateRepoConfig(file)) =>
        ValidateRepoConfigContext.step0[IO].flatMap(_.validateRepoConfigAlg.validateAndReport(file))
      case Cli.ParseResult.Help(help)   => Console[IO].println(help).as(ExitCode.Success)
      case Cli.ParseResult.Error(error) => Console[IO].errorln(error).as(ExitCode.Error)
    }
}
