package org.scalasteward.core.mock

import better.files.File
import org.http4s.Uri
import org.scalasteward.core.application.Cli
import org.scalasteward.core.application.Cli.ParseResult.Success

object MockConfig {
  val mockRoot: File = File.temp / "scala-steward"
  val reposFile: Uri = Uri.unsafeFromString((mockRoot / "repos.md").pathAsString)
  mockRoot.delete(true) // Ensure folder is cleared of previous test files
  private val args: List[String] = List(
    s"--workspace=$mockRoot/workspace",
    s"--repos-file=$reposFile",
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
  val Success(Cli.Usage.Regular(config)) = Cli.parseArgs(args)
}
