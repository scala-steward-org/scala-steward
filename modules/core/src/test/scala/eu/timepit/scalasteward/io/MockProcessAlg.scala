package eu.timepit.scalasteward.io

import better.files.File
import cats.data.State
import eu.timepit.scalasteward.MockState.MockEnv
import eu.timepit.scalasteward.util.Nel

class MockProcessAlg extends ProcessAlg[MockEnv] {
  override def exec(
      command: Nel[String],
      cwd: File,
      extraEnv: (String, String)*
  ): MockEnv[List[String]] =
    State(s => (s.exec(command.toList), List.empty))

  override def execSandboxed(command: Nel[String], cwd: File): MockEnv[List[String]] =
    exec("sandbox" :: command, cwd)
}
