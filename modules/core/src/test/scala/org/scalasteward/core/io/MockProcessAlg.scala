package org.scalasteward.core.io

import better.files.File
import org.scalasteward.core.application.Config
import org.scalasteward.core.mock.{applyPure, MockEff}
import org.scalasteward.core.util.Nel

class MockProcessAlg(implicit config: Config) extends ProcessAlg.UsingFirejail[MockEff](config) {
  override def exec(
      command: Nel[String],
      cwd: File,
      extraEnv: (String, String)*
  ): MockEff[List[String]] =
    applyPure(s => (s.exec(command.toList, extraEnv: _*), List.empty[String]))
}
