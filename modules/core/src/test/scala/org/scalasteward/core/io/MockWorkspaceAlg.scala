package org.scalasteward.core.io

import better.files.File
import cats.data.Kleisli
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.WorkspaceAlg.RunSummaryFileName
import org.scalasteward.core.mock.{MockConfig, MockEff}

class MockWorkspaceAlg extends WorkspaceAlg[MockEff] {
  override def removeAnyRunSpecificFiles: MockEff[Unit] =
    Kleisli.pure(())

  override def rootDir: MockEff[File] =
    Kleisli.pure(MockConfig.config.workspace)

  override def repoDir(repo: Repo): MockEff[File] =
    rootDir.map(_ / repo.owner / repo.repo)

  override def buildRootDir(buildRoot: BuildRoot): MockEff[File] =
    repoDir(buildRoot.repo).map(_ / buildRoot.relativePath)

  def runSummaryFile: MockEff[File] = rootDir.map(_ / RunSummaryFileName)
}
