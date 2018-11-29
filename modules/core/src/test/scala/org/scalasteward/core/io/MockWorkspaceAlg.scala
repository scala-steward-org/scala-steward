package org.scalasteward.core.io

import better.files.File
import cats.data.State
import org.scalasteward.core.MockState.MockEnv
import org.scalasteward.core.github.data.Repo

class MockWorkspaceAlg extends WorkspaceAlg[MockEnv] {
  override def cleanWorkspace: MockEnv[Unit] =
    State.pure(())

  override def rootDir: MockEnv[File] =
    State.pure(File.root / "tmp" / "ws")

  override def repoDir(repo: Repo): MockEnv[File] =
    State.pure(File.root / "tmp" / "ws" / repo.owner / repo.repo)
}
