package eu.timepit.scalasteward.io

import better.files.File
import cats.data.State
import eu.timepit.scalasteward.MockState.MockEnv

class MockProcessAlg extends ProcessAlg[MockEnv] {
  override def exec(command: List[String], cwd: File): MockEnv[List[String]] =
    State(s => (s.exec(command), List.empty))

  override def execSandboxed(command: List[String], cwd: File): MockEnv[List[String]] =
    exec("sandbox" :: command, cwd)
}
