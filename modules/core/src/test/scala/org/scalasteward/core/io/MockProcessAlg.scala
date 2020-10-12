package org.scalasteward.core.io

import better.files.File
import org.scalasteward.core.application.Config.ProcessCfg
import org.scalasteward.core.io.ProcessAlg.UsingFirejail
import org.scalasteward.core.mock.{applyPure, MockEff}
import org.scalasteward.core.util.Nel

class MockProcessAlg(config: ProcessCfg) extends UsingFirejail[MockEff](config.sandboxCfg) {
  private val envVars = config.envVars.map(v => (v.name, v.value))

  override def exec(
      command: Nel[String],
      cwd: File,
      extraEnv: (String, String)*
  ): MockEff[List[String]] =
    applyPure { s =>
      (
        s.exec(cwd.toString :: command.toList, extraEnv ++ envVars: _*),
        s.commandOutputs.getOrElse(command.toList, List.empty)
      )
    }
}
