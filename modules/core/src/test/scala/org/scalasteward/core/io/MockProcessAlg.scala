package org.scalasteward.core.io

import better.files.File
import org.scalasteward.core.application.Config
import org.scalasteward.core.mock.{applyPure, MockEff}
import org.scalasteward.core.util.Nel

class MockProcessAlg(config: Config) extends ProcessAlg.UsingFirejail[MockEff](config) {
  override def exec(
      command: Nel[String],
      cwd: File,
      extraEnv: (String, String)*
  ): MockEff[List[String]] = {
    val env = extraEnv ++ config.envVars.map(v => (v.name, v.value))
    applyPure(s => (s.exec(cwd.toString :: command.toList, env: _*), List.empty[String]))
  }
}
