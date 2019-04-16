package org.scalasteward.core.mock

import better.files.File
import org.scalasteward.core.application.Cli.EnvVar
import org.http4s.Uri
import org.scalasteward.core.application.Config
import org.scalasteward.core.git.{Author, GitAlg}
import org.scalasteward.core.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import org.scalasteward.core.nurture.EditAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.update.FilterAlg
import org.scalasteward.core.util.{DateTimeAlg, LogAlg}
import org.scalasteward.core.vcs.VCSRepoAlg

object MockContext {
  implicit val config: Config = Config(
    workspace = File.temp / "ws",
    reposFile = File.temp / "repos.md",
    gitAuthor = Author("Bot Doe", "bot@example.org"),
    gitHubApiHost = Uri.uri(""),
    gitHubLogin = "bot-doe",
    gitAskPass = File.temp / "askpass.sh",
    signCommits = true,
    whitelistedDirectories = Nil,
    readOnlyDirectories = Nil,
    disableSandbox = false,
    doNotFork = false,
    ignoreOptsFiles = false,
    keepCredentials = false,
    envVars = List(
      EnvVar("TEST_VAR", "GREAT"),
      EnvVar("ANOTHER_TEST_VAR", "ALSO_GREAT")
    )
  )

  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val logger: MockLogger = new MockLogger
  implicit val processAlg: MockProcessAlg = new MockProcessAlg
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg

  implicit val dateTimeAlg: DateTimeAlg[MockEff] = DateTimeAlg.create
  implicit val editAlg: EditAlg[MockEff] = EditAlg.create
  implicit val gitAlg: GitAlg[MockEff] = GitAlg.create
  implicit val gitHubRepoAlg: VCSRepoAlg[MockEff] = VCSRepoAlg.create(config, gitAlg)
  implicit val logAlg: LogAlg[MockEff] = new LogAlg[MockEff]
  implicit val sbtAlg: SbtAlg[MockEff] = SbtAlg.create
  implicit val repoConfigAlg: RepoConfigAlg[MockEff] = new RepoConfigAlg[MockEff]
  implicit val filterAlg: FilterAlg[MockEff] = new FilterAlg[MockEff]
}
