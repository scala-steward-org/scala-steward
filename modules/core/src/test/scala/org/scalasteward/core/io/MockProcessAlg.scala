package org.scalasteward.core.io

import cats.data.Kleisli
import cats.effect.IO
import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.mock.MockEff

object MockProcessAlg {
  def create(config: ProcessCfg): ProcessAlg[MockEff] =
    new ProcessAlg(config)({ args =>
      Kleisli { x =>
        for {
          state <- x.get
          cmd = args.workingDirectory.map(_.toString).toList ++ args.command.toList
          newState = state.exec(cmd, args.extraEnv: _*)
          res <- x
            .set(newState)
            .flatMap { _ =>
              state.commandOutputs
                .getOrElse(args.command.toList, Right(List.empty))
                .fold(err => IO.raiseError(err), a => IO.pure(a))
            }
        } yield res
      }
    })
}
