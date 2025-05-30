package org.scalasteward.core.io

import cats.data.Kleisli
import cats.effect.IO
import cats.syntax.all.*
import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.mock.MockEff
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd

object MockProcessAlg {
  def create(config: ProcessCfg): ProcessAlg[MockEff] =
    new ProcessAlg(config)({ args =>
      Kleisli { ctx =>
        for {
          state0 <- ctx.get
          cmd = Cmd(
            args.extraEnv.map { case (k, v) => s"$k=$v" } ++
              args.workingDirectory.map(_.toString).toList ++
              args.command.toList
          )
          state1 = state0.appendTraceEntry(cmd)
          _ <- ctx.set(state1)
          res <- state1.commandOutputs.get(cmd) match {
            case Some(output)                => IO.fromEither(output).tupleRight(state1.files)
            case None if state1.execCommands =>
              for {
                output <- ProcessAlgTest.ioProcessAlg.execImpl(args)
                // External processes can modify tracked files. We read them again here so that
                // they are in sync with the files in the filesystem.
                files <- state1.files.keys.toList.traverse { file =>
                  FileAlgTest.ioFileAlg.readFile(file).map(_.getOrElse("")).tupleLeft(file)
                }
              } yield (output, files.toMap)
            case None => IO.pure((List.empty, state1.files))
          }
          (output, files) = res
          _ <- ctx.set(state1.copy(files = files))
        } yield output
      }
    })
}
