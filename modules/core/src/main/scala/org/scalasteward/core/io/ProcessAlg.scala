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
import cats.effect.{Blocker, Concurrent, ContextShift, Timer}
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.util.Nel

trait ProcessAlg[F[_]] {
  def exec(command: Nel[String], cwd: File, extraEnv: (String, String)*): F[List[String]]

  def execSandboxed(command: Nel[String], cwd: File, extraEnv: (String, String)*): F[List[String]] =
    exec(command, cwd, extraEnv: _*)
}

object ProcessAlg {
  type ExecImpl[F[_]] = (Nel[String], File, List[(String, String)]) => F[List[String]]

  private class WithoutSandbox[F[_]](config: ProcessCfg)(execImpl: ExecImpl[F])
      extends ProcessAlg[F] {
    val configEnv: List[(String, String)] = config.envVars.map(v => (v.name, v.value))

    override def exec(
        command: Nel[String],
        cwd: File,
        extraEnv: (String, String)*
    ): F[List[String]] =
      execImpl(command, cwd, extraEnv.toList ++ configEnv)
  }

  private class WithFirejail[F[_]](config: ProcessCfg)(execImpl: ExecImpl[F])
      extends WithoutSandbox[F](config)(execImpl) {
    override def execSandboxed(
        command: Nel[String],
        cwd: File,
        extraEnv: (String, String)*
    ): F[List[String]] = {
      val whitelisted = (cwd.pathAsString :: config.sandboxCfg.whitelistedDirectories)
        .map(dir => s"--whitelist=$dir")
      val readOnly = config.sandboxCfg.readOnlyDirectories
        .map(dir => s"--read-only=$dir")
      val envVars = (extraEnv ++ configEnv)
        .map { case (k, v) => s"--env=$k=$v" }
      execImpl(Nel("firejail", whitelisted ++ readOnly ++ envVars) ::: command, cwd, List.empty)
    }
  }

  def fromExecImpl[F[_]](config: ProcessCfg)(execImpl: ExecImpl[F]): ProcessAlg[F] =
    if (config.sandboxCfg.disableSandbox)
      new WithoutSandbox[F](config)(execImpl)
    else
      new WithFirejail[F](config)(execImpl)

  def create[F[_]](blocker: Blocker, config: ProcessCfg)(implicit
      contextShift: ContextShift[F],
      logger: Logger[F],
      timer: Timer[F],
      F: Concurrent[F]
  ): ProcessAlg[F] =
    fromExecImpl(config) { (command, cwd, extraEnv) =>
      logger.debug(s"Execute ${process.showCmd(command, extraEnv)}") >>
        process.slurp[F](
          command,
          Some(cwd.toJava),
          extraEnv,
          config.processTimeout,
          logger.trace(_),
          blocker
        )
    }
}
