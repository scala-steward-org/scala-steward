package org.scalasteward.core.io

import cats.data.Kleisli
import cats.effect.IO
import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.mock.MockEff
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd

object MockProcessAlg {
  def create(config: ProcessCfg): ProcessAlg[MockEff] =
    new ProcessAlg(config)({ args =>
      Kleisli { ctx =>
        for {
          state <- ctx.get
          cmd = Cmd(
            args.extraEnv.map { case (k, v) => s"$k=$v" } ++
              args.workingDirectory.map(_.toString).toList ++
              args.command.toList
          )
          _ <- ctx.set(state.appendTraceEntry(cmd))
          res <- state.commandOutputs.get(cmd) match {
            case Some(output)               => IO.fromEither(output)
            case None if state.execCommands => ProcessAlgTest.ioProcessAlg.execImpl(args)
            case None                       => IO.pure(List.empty)
          }
        } yield res
      }
    })
}
