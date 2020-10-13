/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.io

import better.files.File
import cats.effect.kernel.Async
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config.{ProcessCfg, SandboxCfg}
import org.scalasteward.core.util.Nel

trait ProcessAlg[F[_]] {
  def exec(command: Nel[String], cwd: File, extraEnv: (String, String)*): F[List[String]]

  def execSandboxed(command: Nel[String], cwd: File): F[List[String]]
}

object ProcessAlg {
  abstract class UsingFirejail[F[_]](config: SandboxCfg) extends ProcessAlg[F] {
    override def execSandboxed(command: Nel[String], cwd: File): F[List[String]] =
      if (config.disableSandbox)
        exec(command, cwd)
      else {
        val whitelisted = (cwd.pathAsString :: config.whitelistedDirectories)
          .map(dir => s"--whitelist=$dir")
        val readOnly = config.readOnlyDirectories
          .map(dir => s"--read-only=$dir")
        exec(Nel("firejail", whitelisted ++ readOnly) ::: command, cwd)
      }
  }

  def create[F[_]](blocker: Blocker, config: ProcessCfg)(implicit
      contextShift: ContextShift[F],
      logger: Logger[F],
      timer: Timer[F],
      F: Concurrent[F]
  ): ProcessAlg[F] = {
    val envVars = config.envVars.map(v => (v.name, v.value))
    new UsingFirejail[F](config.sandboxCfg) {
      override def exec(
          command: Nel[String],
          cwd: File,
          extraEnv: (String, String)*
      ): F[List[String]] =
        logger.debug(s"Execute ${command.mkString_(" ")}") >>
          process.slurp[F](
            command,
            Some(cwd.toJava),
            (extraEnv ++ envVars).toMap,
            config.processTimeout,
            logger.trace(_)
          )
    }
  }
}
