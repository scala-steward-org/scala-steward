package org.scalasteward.core.application

import better.files.File
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.Cli.ParseResult._
import scala.concurrent.duration._

class CliTest extends FunSuite {
  test("parseArgs: example") {
    val obtained = Cli.parseArgs(
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
        List("--max-buffer-size", "8192"),
        List("--scalafix-migrations", "/opt/scala-steward/extra-scalafix-migrations.conf"),
        List("--artifact-migrations", "/opt/scala-steward/extra-artifact-migrations.conf"),
        List("--github-app-id", "12345678"),
        List("--github-app-key-file", "example_app_key")
      ).flatten
    )
    val expected = Success(
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
        maxBufferSize = 8192,
        scalafixMigrations = List(uri"/opt/scala-steward/extra-scalafix-migrations.conf"),
        artifactMigrations = Some(File("/opt/scala-steward/extra-artifact-migrations.conf")),
        githubAppId = Some(12345678),
        githubAppKeyFile = Some(File("example_app_key"))
      )
    )
    assertEquals(obtained, expected)
  }

  test("parseArgs: minimal example") {
    val obtained = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f")
      ).flatten
    )
    val expected = Success(
      Cli.Args(
        workspace = File("a"),
        reposFile = File("b"),
        gitAuthorEmail = "d",
        vcsLogin = "e",
        gitAskPass = File("f")
      )
    )
    assertEquals(obtained, expected)
  }

  test("parseArgs: fail if required option not provided") {
    assert(clue(Cli.parseArgs(Nil).asInstanceOf[Error].error).startsWith("Required option"))
  }

  test("parseArgs: unrecognized argument") {
    assert(clue(Cli.parseArgs(List("--foo")).asInstanceOf[Error].error).startsWith("Unrecognized"))
  }

  test("parseArgs: --help") {
    assert(
      clue(Cli.parseArgs(List("--help")).asInstanceOf[Help].help).contains("--git-author-email")
    )
  }

  test("parseArgs: --usage") {
    assert(clue(Cli.parseArgs(List("--usage")).asInstanceOf[Help].help).startsWith("Usage: args"))
  }

  test("envVarArgParser: env-var without equals sign") {
    assert(clue(Cli.envVarArgParser(None, "SBT_OPTS")).isLeft)
  }

  test("envVarArgParser: env-var with multiple equals signs") {
    val value = "-Xss8m -XX:MaxMetaspaceSize=256m"
    assertEquals(Cli.envVarArgParser(None, s"SBT_OPTS=$value"), Right(EnvVar("SBT_OPTS", value)))
  }

  test("finiteDurationArgParser: well-formed duration") {
    assertEquals(Cli.finiteDurationArgParser(None, "30min"), Right(30.minutes))
  }

  test("finiteDurationArgParser: malformed duration") {
    assert(clue(Cli.finiteDurationArgParser(None, "xyz")).isLeft)
  }

  test("finiteDurationArgParser: malformed duration (Inf)") {
    assert(clue(Cli.finiteDurationArgParser(None, "Inf")).isLeft)
  }

  test("finiteDurationArgParser: previous value") {
    assert(clue(Cli.finiteDurationArgParser(Some(10.seconds), "20seconds")).isLeft)
  }

  test("fileArgParser: previous value") {
    assert(clue(Cli.fileArgParser(Some(File("/tmp")), "/opt")).isLeft)
  }

  test("supportedVCSArgParser: unknown value") {
    assert(clue(Cli.supportedVCSArgParser(None, "sourceforge")).isLeft)
  }
}
