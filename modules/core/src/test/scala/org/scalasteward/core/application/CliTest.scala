package org.scalasteward.core.application

import cats.implicits._
import org.http4s.Http4sLiteralSyntax
import org.scalasteward.core.application.Cli.EnvVar
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.duration._

class CliTest extends FunSuite with Matchers {
  type Result[A] = Either[Throwable, A]
  val cli: Cli[Result] = new Cli[Result]

  test("parseArgs") {
    cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--vcs-type", "gitlab"),
        List("--vcs-api-host", "http://example.com"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f"),
        List("--ignore-opts-files"),
        List("--env-var", "g=h"),
        List("--env-var", "i=j"),
        List("--prune-repos"),
        List("--timeout", "30min")
      ).flatten
    ) shouldBe Right(
      Cli.Args(
        workspace = "a",
        reposFile = "b",
        gitAuthorEmail = "d",
        vcsType = SupportedVCS.Gitlab,
        vcsApiHost = uri"http://example.com",
        vcsLogin = "e",
        gitAskPass = "f",
        ignoreOptsFiles = true,
        envVar = List(EnvVar("g", "h"), EnvVar("i", "j")),
        pruneRepos = true,
        timeout = 30.minutes
      )
    )
  }

  test("malformed env-var") {
    cli.parseArgs(List("--env-var", "foo=bar=baz")).isLeft shouldBe true
  }

  test("valid timeout") {
    Cli.finiteDurationParser(None, "30min") shouldBe Right(30.minutes)
  }

  test("malformed timeout") {
    Cli.finiteDurationParser(None, "xyz").isLeft shouldBe true
  }

  test("malformed timeout (Inf)") {
    Cli.finiteDurationParser(None, "Inf").isLeft shouldBe true
  }
}
