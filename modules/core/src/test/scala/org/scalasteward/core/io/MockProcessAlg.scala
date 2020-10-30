package org.scalasteward.core.io

import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.mock.{applyPure, MockEff}

object MockProcessAlg {
  def create(config: ProcessCfg): ProcessAlg[MockEff] =
    ProcessAlg.fromExecImpl(config) { (command, cwd, extraEnv) =>
      applyPure { s =>
        (
          s.exec(cwd.toString :: command.toList, extraEnv: _*),
          s.commandOutputs.getOrElse(command.toList, List.empty)
        )
      }
    }
}
