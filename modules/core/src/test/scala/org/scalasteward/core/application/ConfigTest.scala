package org.scalasteward.core.application

import better.files.File
import org.scalasteward.core.git.Author

object ConfigTest {
  val dummyConfig: Config = Config(
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
    doNotFork = false,
    keepCredentials = false
  )
}
