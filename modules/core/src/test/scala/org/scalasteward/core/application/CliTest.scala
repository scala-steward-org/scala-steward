package org.scalasteward.core.application

import better.files.File
import cats.data.Validated.Valid
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.application.Cli.ParseResult._
import org.scalasteward.core.application.Cli.{EnvVar, Usage}
import org.scalasteward.core.application.ExitCodePolicy.{
  SuccessIfAnyRepoSucceeds,
  SuccessOnlyIfAllReposSucceed
}
import org.scalasteward.core.forge.Forge.{AzureRepos, Bitbucket, GitHub, GitLab, Gitea}
import org.scalasteward.core.util.Nel
import scala.concurrent.duration._

class CliTest extends FunSuite {
  test("parseArgs: default GitHub") {
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(
      List(
        List("--github"),
        List("--workspace", "a"),
        List("--repos-file", "b"),
        List("--git-author-email", "d"),
        List("--forge-api-host", "http://example.com"),
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
    assertEquals(obtained.reposFiles, Nel.one(uri"b"))
    assertEquals(obtained.gitCfg.gitAuthor.email, "d")
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
    assertEquals(obtained.refreshBackoffPeriod, 1.day)
    obtained.forge match {
      case forge: GitHub =>
        assertEquals(forge.apiUri, uri"http://example.com")
        assertEquals(forge.appId, 12345678L)
        assertEquals(forge.appKeyFile, File("example_app_key"))
      case _ => fail(s"forge should be a ${classOf[GitHub].getName} instance")
    }
  }

  private val minimumGithubRequiredParams = List(
    List("--workspace", "a"),
    List("--repos-file", "b"),
    List("--git-author-email", "d"),
    List("--github-app-id", "12345678"),
    List("--github-app-key-file", "example_app_key")
  ).flatten

  test("parseArgs: minimal example for default GitHub") {
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(minimumGithubRequiredParams)

    assert(!obtained.processCfg.sandboxCfg.enableSandbox)
    assertEquals(obtained.workspace, File("a"))
    assertEquals(obtained.reposFiles, Nel.one(uri"b"))
    assertEquals(obtained.gitCfg.gitAuthor.email, "d")
    obtained.forge match {
      case forge: GitHub =>
        assertEquals(forge.appId, 12345678L)
        assertEquals(forge.appKeyFile, File("example_app_key"))
      case _ => fail(s"forge should be a ${classOf[GitHub].getName} instance")
    }
  }

  test("parseArgs: enable sandbox") {
    val params = minimumGithubRequiredParams ++ List("--enable-sandbox")
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params)

    assert(obtained.processCfg.sandboxCfg.enableSandbox)
  }

  test("parseArgs: sandbox parse error") {
    val params = minimumGithubRequiredParams ++ List("--enable-sandbox", "--disable-sandbox")
    val Error(obtained) = Cli.parseArgs(params)

    assert(clue(obtained).startsWith("Unexpected option"))
  }

  test("parseArgs: disable sandbox") {
    val params = minimumGithubRequiredParams ++ List("--disable-sandbox")
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params)

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
    val params = List(
      List("--gitlab"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--gitlab-merge-when-pipeline-succeeds"),
      List("--gitlab-required-reviewers", "5")
    )
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params.flatten)

    obtained.forge match {
      case forge: GitLab =>
        assert(forge.mergeWhenPipelineSucceeds)
        assertEquals(forge.requiredReviewers, Some(5))
      case _ => fail(s"forge should be a ${classOf[GitLab].getName} instance")
    }
  }

  test("parseArgs: invalid GitLab required reviewers") {
    val params = List(
      List("--gitlab"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--gitlab-merge-when-pipeline-succeeds"),
      List("--gitlab-required-reviewers", "-3")
    )
    val Error(errorMsg) = Cli.parseArgs(params.flatten)

    assert(clue(errorMsg).startsWith("Required reviewers must be non-negative"))
  }

  test("parseArgs: validate-repo-config") {
    val params = List(
      List("validate-repo-config", "file.conf")
    ).flatten
    val Success(Usage.ValidateRepoConfig(file)) = Cli.parseArgs(params)

    assertEquals(file, File("file.conf"))
  }

  test("parseArgs: validate fork mode disabled") {
    val params = List(
      List("--azure-repos"),
      List("--forge-api-host", "a"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--azure-repos-organization=some-org")
    )
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params.flatten)

    obtained.forge match {
      case forge: AzureRepos =>
        assertEquals(forge.doNotFork, true)
      case _ => fail(s"forge should be a ${classOf[AzureRepos].getName} instance")
    }
  }

  test("parseArgs: validate no fork mode not allowed") {
    val params = List(
      List("--azure-repos"),
      List("--forge-api-host", "a"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--do-not-fork"),
      List("--azure-repos-organization=some-org")
    )
    val Error(errorMsg) = Cli.parseArgs(params.flatten)

    assert(clue(errorMsg).startsWith("Unexpected option: --do-not-fork"))
  }

  test("parseArgs: validate no fork mode enabled") {
    val params = List(
      List("--gitea"),
      List("--forge-api-host", "a"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--do-not-fork")
    )
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params.flatten)

    obtained.forge match {
      case forge: Gitea =>
        assertEquals(forge.doNotFork, true)
      case _ => fail(s"forge should be a ${classOf[Gitea].getName} instance")
    }
  }

  test("parseArgs: validate pull request labelling disabled") {
    val params = List(
      List("--bitbucket"),
      List("--forge-api-host", "a"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--bitbucket-use-default-reviewers")
    )
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params.flatten)

    obtained.forge match {
      case forge: Bitbucket =>
        assertEquals(forge.addLabels, false)
      case _ => fail(s"forge should be a ${classOf[Bitbucket].getName} instance")
    }
  }

  test("parseArgs: validate pull request labelling not allowed") {
    val params = List(
      List("--bitbucket"),
      List("--forge-api-host", "a"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--bitbucket-use-default-reviewers"),
      List("--add-labels")
    ).flatten
    val Error(errorMsg) = Cli.parseArgs(params)

    assert(clue(errorMsg).startsWith("Unexpected option: --add-labels"))
  }

  test("parseArgs: exit code policy: --exit-code-success-if-any-repo-succeeds") {
    val params = minimumGithubRequiredParams ++ List("--exit-code-success-if-any-repo-succeeds")
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(params)

    assert(obtained.exitCodePolicy == SuccessIfAnyRepoSucceeds)
  }

  test("parseArgs: exit code policy: default") {
    val Success(Usage.Regular(obtained)) = Cli.parseArgs(minimumGithubRequiredParams)
    assert(obtained.exitCodePolicy == SuccessOnlyIfAllReposSucceed)
  }

  test("envVarArgument: env-var without equals sign") {
    assert(clue(Cli.envVarArgument.read("SBT_OPTS")).isInvalid)
  }

  test("envVarArgument: env-var with multiple equals signs") {
    val value = "-Xss8m -XX:MaxMetaspaceSize=256m"
    assertEquals(Cli.envVarArgument.read(s"SBT_OPTS=$value"), Valid(EnvVar("SBT_OPTS", value)))
  }

  test("azure-repos validation") {
    val param = List(
      List("--azure-repos"),
      List("--forge-api-host", "a"),
      List("--workspace", "a"),
      List("--repos-file", "b"),
      List("--git-author-email", "d"),
      List("--forge-login", "e"),
      List("--git-ask-pass", "f"),
      List("--azure-repos-organization")
    ).flatten
    val Error(error) = Cli.parseArgs(param)
    assert(error.startsWith("Missing value for option: --azure-repos-organization"))
  }
}
