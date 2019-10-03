package org.scalasteward.core.io

import better.files.File
import cats.data.StateT
import org.scalasteward.core.mock.{MockContext, MockEff}
import org.scalasteward.core.vcs.data.Repo

class MockWorkspaceAlg extends WorkspaceAlg[MockEff] {
  override def cleanWorkspace: MockEff[Unit] =
    StateT.pure(())

  override def rootDir: MockEff[File] =
    StateT.pure(MockContext.config.workspace)

  override def repoDir(repo: Repo): MockEff[File] =
    rootDir.map(_ / repo.owner / repo.repo)
}
