package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.mock.MockEff

class MockWorkspaceAlg extends WorkspaceAlg[MockEff] {
  override def cleanWorkspace: MockEff[Unit] =
    StateT.pure(())

  override def rootDir: MockEff[File] =
    StateT.pure(File.root / "tmp" / "ws")

  override def repoDir(repo: Repo): MockEff[File] =
    StateT.pure(File.root / "tmp" / "ws" / repo.owner / repo.repo)
}
