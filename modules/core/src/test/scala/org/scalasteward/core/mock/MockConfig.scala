package org.scalasteward.core.mock

import better.files.File
import org.scalasteward.core.application.Cli.ParseResult.Success
import org.scalasteward.core.application.{Cli, Config}
import org.scalasteward.core.git.FileGitAlg

object MockConfig {
  val mockRoot: File = File.temp / "scala-steward"
  private val args: List[String] = List(
    s"--workspace=$mockRoot/workspace",
    s"--repos-file=$mockRoot/repos.md",
    "--git-author-name=Bot Doe",
    "--git-author-email=bot@example.org",
    s"--git-ask-pass=$mockRoot/askpass.sh",
    "--forge-api-host=http://example.com",
    "--forge-login=bot-doe",
    "--enable-sandbox",
    "--env-var=VAR1=val1",
    "--env-var=VAR2=val2",
    "--cache-ttl=1hour",
    "--refresh-backoff-period=1hour"
  )
  val Success(Config.StewardUsage.Regular(config)) = Cli.parseArgs(args)
  val envVars: List[String] =
    List(s"GIT_ASKPASS=${config.gitCfg.gitAskPass}", "VAR1=val1", "VAR2=val2")
  def gitCmd(repoDir: File): List[String] =
    envVars ++ (repoDir.toString :: FileGitAlg.gitCmd.toList)
}
