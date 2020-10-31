package org.scalasteward.core.io

import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.mock.{applyPure, MockEff}

object MockProcessAlg {
  def create(config: ProcessCfg): ProcessAlg[MockEff] =
    ProcessAlg.fromExecImpl(config) { args =>
      applyPure { s =>
        val cmd = args.workingDirectory.map(_.toString).toList ++ args.command.toList
        val s1 = s.exec(cmd, args.extraEnv: _*)
        val a = s.commandOutputs.getOrElse(args.command.toList, List.empty)
        (s1, a)
      }
    }
}
