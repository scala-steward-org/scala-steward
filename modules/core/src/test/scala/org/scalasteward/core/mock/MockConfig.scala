package org.scalasteward.core.mock

import better.files.File
import org.scalasteward.core.application.Cli.ParseResult.Success
import org.scalasteward.core.application.{Cli, Config}

object MockConfig {
  val mockRoot: File = File.temp / "scala-steward"
  mockRoot.delete(true) // Ensure folder is cleared of previous test files
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
    "--add-labels",
    "--refresh-backoff-period=1hour"
  )
  val Success(Config.StewardUsage.Regular(config)) = Cli.parseArgs(args)
}
