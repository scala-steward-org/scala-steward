package org.scalasteward.core.io

import better.files.File
import org.scalasteward.core.mock.{applyPure, MockContext, MockEff}
import org.scalasteward.core.util.Nel

class MockProcessAlg extends ProcessAlg.UsingFirejail[MockEff](MockContext.config) {
  override def exec(
      command: Nel[String],
      cwd: File,
      extraEnv: (String, String)*
  ): MockEff[List[String]] =
    applyPure(s => (s.exec(command.toList), List.empty[String]))
}
