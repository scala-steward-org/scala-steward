package org.scalasteward.core.mock

import better.files.File
import org.http4s.Uri
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.{Cli, Config}
import org.scalasteward.core.vcs.VCSType
import org.scalasteward.core.vcs.data.AuthenticatedUser

import scala.concurrent.duration._

object MockConfig {
  val mockRoot: File = File.temp / "scala-steward"
  val args: Cli.Args = Cli.Args(
    workspace = mockRoot / "workspace",
    reposFile = mockRoot / "repos.md",
    defaultRepoConf = Some(mockRoot / "default.scala-steward.conf"),
    gitAuthorName = "Bot Doe",
    gitAuthorEmail = "bot@example.org",
    vcsType = VCSType.GitHub,
    vcsApiHost = Uri(),
    vcsLogin = "bot-doe",
    gitAskPass = mockRoot / "askpass.sh",
    enableSandbox = Some(true),
    envVar = List(EnvVar("VAR1", "val1"), EnvVar("VAR2", "val2")),
    cacheTtl = 1.hour
  )
  val config: Config = Config.from(args)
  val envVars = List(s"GIT_ASKPASS=${config.gitCfg.gitAskPass}", "VAR1=val1", "VAR2=val2")
  val user: AuthenticatedUser = AuthenticatedUser("scala-steward", "token")
}
