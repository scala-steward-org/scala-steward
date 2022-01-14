package org.scalasteward.core.application

import better.files.File
import cats.data.Validated.Valid
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.Cli.ParseResult._
import org.scalasteward.core.vcs.VCSType
import org.scalasteward.core.vcs.github.GitHubApp

import scala.concurrent.duration._

class CliTest extends FunSuite {
  test("parseArgs: example") {
    val Success(obtained) = Cli.parseArgs(
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
        List("--process-timeout", "30min"),
        List("--max-buffer-size", "1234"),
        List("--scalafix-migrations", "/opt/scala-steward/extra-scalafix-migrations.conf"),
        List("--artifact-migrations", "/opt/scala-steward/extra-artifact-migrations.conf"),
        List("--repo-config", "/opt/scala-steward/scala-steward.conf"),
        List("--github-app-id", "12345678"),
        List("--github-app-key-file", "example_app_key"),
        List("--refresh-backoff-period", "1 day")
      ).flatten
    )

    assertEquals(obtained.workspace, File("a"))
    assertEquals(obtained.reposFile, File("b"))
    assertEquals(obtained.gitCfg.gitAuthor.email, "d")
    assertEquals(obtained.gitCfg.gitAskPass, File("f"))
    assertEquals(obtained.vcsCfg.tpe, VCSType.GitLab)
    assertEquals(obtained.vcsCfg.apiHost, uri"http://example.com")
    assertEquals(obtained.vcsCfg.login, "e")
    assertEquals(obtained.ignoreOptsFiles, true)
    assertEquals(obtained.processCfg.envVars, List(EnvVar("g", "h"), EnvVar("i", "j")))
    assertEquals(obtained.processCfg.processTimeout, 30.minutes)
    assertEquals(obtained.processCfg.maxBufferSize, 1234)
    assertEquals(
      obtained.repoConfigCfg.repoConfigs,
      List(uri"/opt/scala-steward/scala-steward.conf")
    )
    assertEquals(
      obtained.scalafixCfg.migrations,
      List(uri"/opt/scala-steward/extra-scalafix-migrations.conf")
    )
    assertEquals(
      obtained.artifactCfg.migrations,
      List(uri"/opt/scala-steward/extra-artifact-migrations.conf")
    )
    assertEquals(obtained.githubApp, Some(GitHubApp(12345678L, File("example_app_key"))))
    assertEquals(obtained.refreshBackoffPeriod, 1.day)
  }

  test("parseArgs: minimal example") {
    val Success(obtained) = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f")
      ).flatten
    )

    assert(!obtained.processCfg.sandboxCfg.enableSandbox)
    assertEquals(obtained.workspace, File("a"))
    assertEquals(obtained.reposFile, File("b"))
    assertEquals(obtained.gitCfg.gitAuthor.email, "d")
    assertEquals(obtained.gitCfg.gitAskPass, File("f"))
    assertEquals(obtained.vcsCfg.login, "e")
  }

  test("parseArgs: enable sandbox") {
    val Success(obtained) = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f"),
        List("--enable-sandbox")
      ).flatten
    )

    assert(obtained.processCfg.sandboxCfg.enableSandbox)
  }

  test("parseArgs: sandbox parse error") {
    val Error(obtained) = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f"),
        List("--enable-sandbox"),
        List("--disable-sandbox")
      ).flatten
    )

    assert(clue(obtained).startsWith("Unexpected option"))
  }

  test("parseArgs: disable sandbox") {
    val Success(obtained) = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--vcs-login", "e"),
        List("--git-ask-pass", "f"),
        List("--disable-sandbox")
      ).flatten
    )

    assert(!obtained.processCfg.sandboxCfg.enableSandbox)
  }

  test("parseArgs: fail if required option not provided") {
    val Error(obtained) = Cli.parseArgs(Nil)
    assert(clue(obtained).startsWith("Missing expected"))
  }

  test("parseArgs: unrecognized argument") {
    val Error(obtained) = Cli.parseArgs(List("--foo"))
    assert(clue(obtained).startsWith("Unexpected option"))
  }

  test("parseArgs: --help") {
    val Help(obtained) = Cli.parseArgs(List("--help"))
    assert(clue(obtained).startsWith("Usage"))
  }

  test("envVarArgument: env-var without equals sign") {
    assert(clue(Cli.envVarArgument.read("SBT_OPTS")).isInvalid)
  }

  test("envVarArgument: env-var with multiple equals signs") {
    val value = "-Xss8m -XX:MaxMetaspaceSize=256m"
    assertEquals(Cli.envVarArgument.read(s"SBT_OPTS=$value"), Valid(EnvVar("SBT_OPTS", value)))
  }

  test("vcsTypeArgument: unknown value") {
    assert(clue(Cli.vcsTypeArgument.read("sourceforge")).isInvalid)
  }
}
