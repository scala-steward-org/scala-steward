package org.scalasteward.core.io

import better.files.File
import cats.data.State
import org.scalasteward.core.MockState.MockEnv
import org.scalasteward.core.application.ConfigTest
import org.scalasteward.core.util.Nel

class MockProcessAlg extends ProcessAlg.UsingFirejail[MockEnv](ConfigTest.dummyConfig) {
  override def exec(
      command: Nel[String],
      cwd: File,
      extraEnv: (String, String)*
  ): MockEnv[List[String]] =
    State(s => (s.exec(command.toList), List.empty))
}
