package org.scalasteward.core.mock

import better.files.File
import org.scalasteward.core.application.Config
import org.scalasteward.core.git.{Author, GitAlg}
import org.scalasteward.core.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import org.scalasteward.core.nurture.EditAlg
import org.scalasteward.core.sbt.SbtAlg

object MockContext {
  implicit val config: Config = Config(
    workspace = File.temp,
    reposFile = File.temp / "repos.md",
    gitAuthor = Author("", ""),
    gitHubApiHost = "",
    gitHubLogin = "",
    gitAskPass = File.temp / "askpass.sh",
    signCommits = true,
    whitelistedDirectories = Nil,
    readOnlyDirectories = Nil,
    disableSandbox = false,
    doNotFork = false
  )

  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val logger: MockLogger = new MockLogger
  implicit val processAlg: MockProcessAlg = new MockProcessAlg
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg

  implicit val editAlg: EditAlg[MockEff] = EditAlg.create
  implicit val gitAlg: GitAlg[MockEff] = GitAlg.create
  implicit val sbtAlg: SbtAlg[MockEff] = SbtAlg.create
}
