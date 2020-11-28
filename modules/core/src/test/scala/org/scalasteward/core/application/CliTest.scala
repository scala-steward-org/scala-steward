package org.scalasteward.core.application

import better.files.File
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.Cli.ParseResult._
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class CliTest extends AnyFunSuite with Matchers with EitherValues {
  test("parseArgs: example") {
    Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--default-repo-conf", "c"),
        List("--git-author-email", "d"),
        List("--vcs-type", "gitlab"),
        List("--vcs-api-host", "http://example.com"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f"),
        List("--ignore-opts-files"),
        List("--env-var", "g=h"),
        List("--env-var", "i=j"),
        List("--process-timeout", "30min"),
        List("--scalafix-migrations", "/opt/scala-steward/extra-scalafix-migrations.conf"),
        List("--artifact-migrations", "/opt/scala-steward/extra-artifact-migrations.conf"),
        List("--github-app-id", "12345678"),
        List("--github-app-key-file", "example_app_key")
      ).flatten
    ) shouldBe Success(
      Cli.Args(
        workspace = File("a"),
        reposFile = File("b"),
        defaultRepoConf = Some(File("c")),
        gitAuthorEmail = "d",
        vcsType = SupportedVCS.GitLab,
        vcsApiHost = uri"http://example.com",
        vcsLogin = "e",
        gitAskPass = File("f"),
        ignoreOptsFiles = true,
        envVar = List(EnvVar("g", "h"), EnvVar("i", "j")),
        processTimeout = 30.minutes,
        scalafixMigrations = List(uri"/opt/scala-steward/extra-scalafix-migrations.conf"),
        artifactMigrations = Some(File("/opt/scala-steward/extra-artifact-migrations.conf")),
        githubAppId = Some(12345678),
        githubAppKeyFile = Some(File("example_app_key"))
      )
    )
  }

  test("parseArgs: minimal example") {
    Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f")
      ).flatten
    ) shouldBe Success(
      Cli.Args(
        workspace = File("a"),
        reposFile = File("b"),
        gitAuthorEmail = "d",
        vcsLogin = "e",
        gitAskPass = File("f")
      )
    )
  }

  test("parseArgs: fail if required option not provided") {
    Cli.parseArgs(Nil).asInstanceOf[Error].error should startWith("Required option")
  }

  test("parseArgs: unrecognized argument") {
    Cli.parseArgs(List("--foo")).asInstanceOf[Error].error should startWith("Unrecognized")
  }

  test("parseArgs: --help") {
    Cli.parseArgs(List("--help")).asInstanceOf[Help].help should include("--git-author-email")
  }

  test("parseArgs: --usage") {
    Cli.parseArgs(List("--usage")).asInstanceOf[Help].help should startWith("Usage: args")
  }

  test("envVarArgParser: env-var without equals sign") {
    Cli.envVarArgParser(None, "SBT_OPTS").isLeft shouldBe true
  }

  test("envVarArgParser: env-var with multiple equals signs") {
    val value = "-Xss8m -XX:MaxMetaspaceSize=256m"
    Cli.envVarArgParser(None, s"SBT_OPTS=$value") shouldBe Right(EnvVar("SBT_OPTS", value))
  }

  test("finiteDurationArgParser: well-formed duration") {
    Cli.finiteDurationArgParser(None, "30min") shouldBe Right(30.minutes)
  }

  test("finiteDurationArgParser: malformed duration") {
    Cli.finiteDurationArgParser(None, "xyz").isLeft shouldBe true
  }

  test("finiteDurationArgParser: malformed duration (Inf)") {
    Cli.finiteDurationArgParser(None, "Inf").isLeft shouldBe true
  }

  test("finiteDurationArgParser: previous value") {
    Cli.finiteDurationArgParser(Some(10.seconds), "20seconds").isLeft shouldBe true
  }

  test("fileArgParser: previous value") {
    Cli.fileArgParser(Some(File("/tmp")), "/opt").isLeft shouldBe true
  }

  test("supportedVCSArgParser: unknown value") {
    Cli.supportedVCSArgParser(None, "sourceforge").isLeft shouldBe true
  }
}
