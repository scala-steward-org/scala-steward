package org.scalasteward.core.application

import cats.implicits._
import org.http4s.Uri
import org.scalatest.{FunSuite, Matchers}

class CliTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]
  val cli: Cli[Result] = Cli.create[Result]

  test("parseArgs") {
    cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-name", "c"),
        List("--git-author-email", "d"),
        List("--github-api-host", "http://example.com"),
        List("--github-login", "e"),
        List("--git-ask-pass", "f")
      ).flatten
    ) shouldBe Right(
      Cli.Args(
        workspace = "a",
        reposFile = "b",
        gitAuthorName = "c",
        gitAuthorEmail = "d",
        githubApiHost = Uri.uri("http://example.com"),
        githubLogin = "e",
        gitAskPass = "f"
      )
    )
  }
}
