package org.scalasteward.core.application

import better.files.File
import cats.data.Validated.Valid
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Cli.ParseResult._
import org.scalasteward.core.application.Cli.{EnvVar, Usage}
import org.scalasteward.core.forge.ForgeType
import org.scalasteward.core.forge.github.GitHubApp
import org.scalasteward.core.util.Nel
import scala.concurrent.duration._

class CliTest extends FunSuite {
  test("parseArgs: example") {
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--forge-type", "gitlab"),
        List("--forge-api-host", "http://example.com"),
        List("--forge-login", "e"),
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
        List("--refresh-backoff-period", "1 day"),
        List("--bitbucket-use-default-reviewers")
      ).flatten
    )

    assertEquals(obtained.workspace, File("a"))
    assertEquals(obtained.reposFiles, Nel.one(uri"b"))
    assertEquals(obtained.gitCfg.gitAuthor.email, "d")
    assertEquals(obtained.gitCfg.gitAskPass, File("f"))
    assertEquals(obtained.forgeCfg.tpe, ForgeType.GitLab)
    assertEquals(obtained.forgeCfg.apiHost, uri"http://example.com")
    assertEquals(obtained.forgeCfg.login, "e")
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
    assert(!obtained.gitLabCfg.mergeWhenPipelineSucceeds)
    assertEquals(obtained.gitLabCfg.requiredReviewers, None)
    assert(obtained.bitbucketCfg.useDefaultReviewers)
    assert(!obtained.bitbucketServerCfg.useDefaultReviewers)
  }

  private val minimumRequiredParams = List(
    List("--workspace", "a"),
    List("--repos-file", "b"),
    List("--git-author-email", "d"),
    List("--forge-login", "e"),
    List("--git-ask-pass", "f"),
    List("--disable-sandbox")
  )

  test("parseArgs: minimal example") {
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(
      minimumRequiredParams.flatten
    )

    assert(!obtained.processCfg.sandboxCfg.enableSandbox)
    assertEquals(obtained.workspace, File("a"))
    assertEquals(obtained.reposFiles, Nel.one(uri"b"))
    assertEquals(obtained.gitCfg.gitAuthor.email, "d")
    assertEquals(obtained.gitCfg.gitAskPass, File("f"))
    assertEquals(obtained.forgeCfg.login, "e")
  }

  test("parseArgs: enable sandbox") {
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--forge-login", "e"),
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
        List("--forge-login", "e"),
        List("--git-ask-pass", "f"),
        List("--enable-sandbox"),
        List("--disable-sandbox")
      ).flatten
    )

    assert(clue(obtained).startsWith("Unexpected option"))
  }

  test("parseArgs: disable sandbox") {
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(
      List(
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--forge-login", "e"),
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

  test("parseArgs: non-default GitLab arguments") {
    val params = minimumRequiredParams ++ List(
      List("--gitlab-merge-when-pipeline-succeeds"),
      List("--gitlab-required-reviewers", "5")
    )
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params.flatten)

    assert(obtained.gitLabCfg.mergeWhenPipelineSucceeds)
    assertEquals(obtained.gitLabCfg.requiredReviewers, Some(5))
  }

  test("parseArgs: invalid GitLab required reviewers") {
    val params = minimumRequiredParams ++ List(
      List("--gitlab-merge-when-pipeline-succeeds"),
      List("--gitlab-required-reviewers", "-3")
    )
    val Error(errorMsg) = Cli.parseArgs(params.flatten)

    assert(clue(errorMsg).startsWith("Required reviewers must be non-negative"))
  }

  test("parseArgs: validate-repo-config") {
    val Success(Usage.ValidateRepoConfig(file)) = Cli.parseArgs(
      List(
        List("validate-repo-config", "file.conf")
      ).flatten
    )

    assertEquals(file, File("file.conf"))
  }

  test("parseArgs: validate fork mode disabled") {
    val params = minimumRequiredParams ++ List(
      List("--forge-type", "azure-repos"),
      List("--do-not-fork")
    )
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params.flatten)
    assert(obtained.forgeCfg.doNotFork)
  }

  test("parseArgs: validate fork mode enabled") {
    val params = minimumRequiredParams ++ List(
      List("--forge-type", "azure-repos")
    )
    val Error(errorMsg) = Cli.parseArgs(params.flatten)
    assert(clue(errorMsg).startsWith("azure-repos, bitbucket-server do not support fork mode"))
  }

  test("parseArgs: validate pull request labeling disabled") {
    val params = minimumRequiredParams ++ List(
      List("--forge-type", "bitbucket")
    )
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params.flatten)
    assert(!obtained.forgeCfg.addLabels)
  }

  test("parseArgs: validate pull request labeling enabled") {
    val params = minimumRequiredParams ++ List(
      List("--forge-type", "bitbucket"),
      List("--add-labels")
    )
    val Error(errorMsg) = Cli.parseArgs(params.flatten)
    assert(
      clue(errorMsg).startsWith("bitbucket, bitbucket-server do not support pull request labels")
    )
  }

  test("envVarArgument: env-var without equals sign") {
    assert(clue(Cli.envVarArgument.read("SBT_OPTS")).isInvalid)
  }

  test("envVarArgument: env-var with multiple equals signs") {
    val value = "-Xss8m -XX:MaxMetaspaceSize=256m"
    assertEquals(Cli.envVarArgument.read(s"SBT_OPTS=$value"), Valid(EnvVar("SBT_OPTS", value)))
  }

  test("forgeTypeArgument: unknown value") {
    assert(clue(Cli.forgeTypeArgument.read("sourceforge")).isInvalid)
  }

  test("azure-repos validation") {
    val Error(error) = Cli.parseArgs(
      (minimumRequiredParams ++ List(
        List("--forge-type", "azure-repos"),
        List("--azure-repos-organization")
      )).flatten
    )
    assert(error.startsWith("Missing value for option: --azure-repos-organization"))
  }
}
