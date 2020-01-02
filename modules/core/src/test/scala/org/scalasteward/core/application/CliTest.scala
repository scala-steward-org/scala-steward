package org.scalasteward.core.application

import cats.implicits._
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Cli.EnvVar
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class CliTest extends AnyFunSuite with Matchers {
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
        List("--process-timeout", "30min")
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
        processTimeout = 30.minutes
      )
    )
  }

  test("env-var without equals sign") {
    Cli.envVarArgParser(None, "SBT_OPTS").isLeft shouldBe true
  }

  test("env-var with multiple equals signs") {
    val value = "-Xss8m -XX:MaxMetaspaceSize=256m"
    Cli.envVarArgParser(None, s"SBT_OPTS=$value") shouldBe Right(EnvVar("SBT_OPTS", value))
  }

  test("valid timeout") {
    Cli.finiteDurationArgParser(None, "30min") shouldBe Right(30.minutes)
  }

  test("malformed timeout") {
    Cli.finiteDurationArgParser(None, "xyz").isLeft shouldBe true
  }

  test("malformed timeout (Inf)") {
    Cli.finiteDurationArgParser(None, "Inf").isLeft shouldBe true
  }
}
