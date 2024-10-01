package org.scalasteward.core.forge

import better.files.File
import munit.CatsEffectSuite
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.http4s.syntax.all._
import org.http4s.{AuthScheme, BasicCredentials, Headers, Method, Request}
import org.scalasteward.core.forge.Forge.{GitHub, Gitea}
import org.scalasteward.core.forge.github.GitHubApiAlg.acceptHeaderVersioned
import org.scalasteward.core.forge.github.Repository
import org.scalasteward.core.io.ProcessAlg
import org.scalasteward.core.mock.MockConfig.{gitHubConfig, key}
import org.scalasteward.core.mock.MockContext.context.{httpJsonClient, logger, workspaceAlg}
import org.scalasteward.core.mock.{GitHubAuth, MockEff, MockState}

class ForgeAuthAlgTest extends CatsEffectSuite {
  test("authenticate Gitea API") {
    val credentials = BasicCredentials("user", "pass")
    implicit val processAlg: ProcessAlg[MockEff] =
      new ProcessAlg(gitHubConfig.processCfg)(_ => MockEff.pure(List(credentials.password)))
    val forge = Gitea(
      apiUri = uri"https://git.example.com/api/v1",
      login = credentials.username,
      gitAskPass = File.newTemporaryFile(),
      doNotFork = false,
      addLabels = false
    )
    val obtained = ForgeAuthAlg
      .create[MockEff](forge)
      .authenticateApi(Request[MockEff](method = Method.GET, uri = uri""))
      .runA(MockState.empty)
      .map(_.headers)
    val expected = Headers(Authorization(credentials))
    obtained.map(assertEquals(_, expected))
  }

  test("authenticate Gitea Git") {
    val credentials = BasicCredentials("user", "pass")
    implicit val processAlg: ProcessAlg[MockEff] =
      new ProcessAlg(gitHubConfig.processCfg)(_ => MockEff.pure(List(credentials.password)))
    val forge = Gitea(
      apiUri = uri"https://git.example.com/api/v1",
      login = credentials.username,
      gitAskPass = File.newTemporaryFile(),
      doNotFork = false,
      addLabels = false
    )
    val obtained = ForgeAuthAlg
      .create[MockEff](forge)
      .authenticateGit(uri"https://git.example.com/user/repo.git")
      .runA(MockState.empty)
    val expected = uri"https://user:pass@git.example.com/user/repo.git"
    obtained.map(assertEquals(_, expected))
  }

  test("authenticate GitHub API") {
    val state = MockState.empty.copy(clientResponses = GitHubAuth.api(List(Repository("user/bla"))))
    implicit val processAlg: ProcessAlg[MockEff] =
      new ProcessAlg(gitHubConfig.processCfg)(_ => MockEff.pure(List.empty))
    val forge = GitHub(
      apiUri = uri"https://git.example.com",
      doNotFork = false,
      addLabels = false,
      appId = 1L,
      appKeyFile = key
    )
    val obtained = ForgeAuthAlg
      .create[MockEff](forge)
      .authenticateApi(
        Request[MockEff](method = Method.GET, uri = uri"https://git.example.com/repos/user/bla")
      )
      .runA(state)
      .map(_.headers)
    val expected =
      Headers(Authorization(Token(AuthScheme.Bearer, "some-token")), acceptHeaderVersioned)
    obtained.map(assertEquals(_, expected))
  }
}
