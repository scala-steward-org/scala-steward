/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.mock

import better.files.File
import org.http4s.Uri
import org.scalasteward.core.application.Cli
import org.scalasteward.core.application.Cli.ParseResult.Success
import scala.io.Source

object MockConfig {
  val mockRoot: File = File.temp / "scala-steward"
  val reposFile: Uri = Uri.unsafeFromString((mockRoot / "repos.md").pathAsString)
  mockRoot.delete(swallowIOExceptions = true) // Ensure folder is cleared of previous test files

  mockRoot.createDirectory()
  val key = mockRoot / "rsa-4096-private.pem"
  key.overwrite(Source.fromResource("rsa-4096-private.pem").mkString)

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
    "--github-app-id=1234",
    s"--github-app-key-file=$key",
    "--refresh-backoff-period=1hour"
  )
  val Success(Cli.Usage.Regular(config)) = Cli.parseArgs(args): @unchecked
}
