package eu.timepit.scalasteward.io

import better.files.File
import cats.data.State
import eu.timepit.scalasteward.MockState.MockEnv
import eu.timepit.scalasteward.github.data.Repo

class MockWorkspaceAlg extends WorkspaceAlg[MockEnv] {
  override def cleanWorkspace: MockEnv[Unit] =
    State.pure(())

  override def rootDir: MockEnv[File] =
    State.pure(File.root / "tmp" / "ws")

  override def repoDir(repo: Repo): MockEnv[File] =
    State.pure(File.root / "tmp" / "ws" / repo.owner / repo.repo)
}
