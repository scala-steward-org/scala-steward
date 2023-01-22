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

package org.scalasteward.core.io

import better.files.File
import cats.effect.Async
import cats.syntax.all._
import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.io.process.{Args, SlurpOption, SlurpOptions}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

trait ProcessAlg[F[_]] {
  def exec(
      command: Nel[String],
      workingDirectory: File,
      extraEnv: List[(String, String)] = Nil,
      slurpOptions: SlurpOptions = Set.empty
  ): F[List[String]]

  def execSandboxed(
      command: Nel[String],
      workingDirectory: File,
      extraEnv: List[(String, String)] = Nil,
      slurpOptions: SlurpOptions = Set.empty
  ): F[List[String]] =
    exec(command, workingDirectory, extraEnv, slurpOptions)

  final def execMaybeSandboxed(sandboxed: Boolean)(
      command: Nel[String],
      workingDirectory: File,
      extraEnv: List[(String, String)] = Nil,
      slurpOptions: SlurpOptions = Set.empty
  ): F[List[String]] =
    if (sandboxed) execSandboxed(command, workingDirectory, extraEnv, slurpOptions)
    else exec(command, workingDirectory, extraEnv, slurpOptions)
}

object ProcessAlg {
  private class NoSandbox[F[_]](config: ProcessCfg)(execImpl: Args => F[List[String]])
      extends ProcessAlg[F] {
    val configEnv: List[(String, String)] = config.envVars.map(v => (v.name, v.value))

    override def exec(
        command: Nel[String],
        workingDirectory: File,
        extraEnv: List[(String, String)],
        slurpOptions: SlurpOptions
    ): F[List[String]] =
      execImpl(Args(command, Some(workingDirectory), extraEnv ++ configEnv, slurpOptions))
  }

  private class WithFirejail[F[_]](config: ProcessCfg)(execImpl: Args => F[List[String]])
      extends NoSandbox[F](config)(execImpl) {
    override def execSandboxed(
        command: Nel[String],
        workingDirectory: File,
        extraEnv: List[(String, String)],
        slurpOptions: SlurpOptions
    ): F[List[String]] = {
      val whitelisted = (workingDirectory.toString :: config.sandboxCfg.whitelistedDirectories)
        .map(dir => s"--whitelist=$dir")
      val readOnly = config.sandboxCfg.readOnlyDirectories
        .map(dir => s"--read-only=$dir")
      val envVars = (extraEnv ++ configEnv)
        .map { case (k, v) => s"--env=$k=$v" }
      val firejail = Nel("firejail", "--quiet" :: whitelisted ++ readOnly ++ envVars) ::: command
      val args = Args(
        command = firejail,
        workingDirectory = Some(workingDirectory),
        slurpOptions = slurpOptions ++ Set(SlurpOption.ClearEnvironment)
      )
      execImpl(args)
    }
  }

  def fromExecImpl[F[_]](config: ProcessCfg)(execImpl: Args => F[List[String]]): ProcessAlg[F] =
    if (config.sandboxCfg.enableSandbox)
      new WithFirejail[F](config)(execImpl)
    else
      new NoSandbox[F](config)(execImpl)

  def create[F[_]](config: ProcessCfg)(implicit
      logger: Logger[F],
      F: Async[F]
  ): ProcessAlg[F] =
    fromExecImpl(config) { args =>
      logger.debug(s"Execute ${process.showCmd(args)}") >>
        process.slurp(args, config.processTimeout, config.maxBufferSize, logger.trace(_))
    }
}
