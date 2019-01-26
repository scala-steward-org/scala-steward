package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import org.scalasteward.core.MockState.MockEnv
import org.scalasteward.core.github.data.Repo

class MockWorkspaceAlg extends WorkspaceAlg[MockEnv] {
  override def cleanWorkspace: MockEnv[Unit] =
    StateT.pure(())

  override def rootDir: MockEnv[File] =
    StateT.pure(File.root / "tmp" / "ws")

  override def repoDir(repo: Repo): MockEnv[File] =
    StateT.pure(File.root / "tmp" / "ws" / repo.owner / repo.repo)
}
