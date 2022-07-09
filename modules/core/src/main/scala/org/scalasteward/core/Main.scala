/*
 * Copyright 2018-2022 Scala Steward contributors
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
import org.scalasteward.core.application.{Cli, Context}
import org.scalasteward.core.repoconfig.ValidateRepoConfigAlg.ConfigValidationResult.Ok
import org.scalasteward.core.repoconfig.ValidateRepoConfigAlg.ConfigValidationResult.FileDoesNotExist
import org.scalasteward.core.repoconfig.ValidateRepoConfigAlg.ConfigValidationResult.ConfigIsInvalid

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Cli.parseArgs(args) match {
      case Cli.ParseResult.Success(config) =>
        Context
          .step0[IO](config)
          .use(ctx =>
            config.validateRepoConfig match {
              case None => ctx.stewardAlg.runF
              case Some(file) =>
                ctx.validateRepoConfigAlg.validateConfigFile(file).flatMap {
                  case Ok =>
                    Console[IO]
                      .println(s"Configuration file at $file is valid.")
                      .as(ExitCode.Success)
                  case FileDoesNotExist =>
                    Console[IO]
                      .println(s"Configuration file at $file does not exist!")
                      .as(ExitCode.Error)
                  case ConfigIsInvalid(err) =>
                    Console[IO]
                      .println(s"Configuration file at $file contains errors:\n  $err")
                      .as(ExitCode.Error)
                }
            }
          )
      case Cli.ParseResult.Help(help)   => Console[IO].println(help).as(ExitCode.Success)
      case Cli.ParseResult.Error(error) => Console[IO].errorln(error).as(ExitCode.Error)
    }
}
