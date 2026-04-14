package org.scalasteward.core.forge.bitbucket

import better.files.File
import munit.CatsEffectSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.io.{MockProcessAlg, MockWorkspaceAlg}
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockConfig, MockEff, MockEffOps, MockState}

class BitbucketAuthAlgTest extends CatsEffectSuite {
  private val apiUri = uri"http://example.com"
  private val cloneUri = uri"https://bitbucket.example.com/scm/PROJ/repo.git"
  private val login = "john-doe"
  private val gitAskPass: File = MockConfig.config.gitCfg.gitAskPass

  test("authenticateGit uses x-bitbucket-api-token-auth and askpass token") {
    implicit val workspaceAlg = new MockWorkspaceAlg
    implicit val processAlg = MockProcessAlg.create(MockConfig.config.processCfg)
    val authAlg = new BitbucketAuthAlg[MockEff](apiUri, login, gitAskPass)
    val prompt = "Password for 'http://john-doe@example.com': "
    val expectedCmd = Cmd.exec(MockConfig.config.workspace, gitAskPass.pathAsString, prompt)
    val state = MockState.empty.copy(
      commandOutputs = Map(expectedCmd -> Right(List("user-api-token")))
    )

    val stateOp = authAlg.authenticateGit(cloneUri).runSA(state)

    stateOp.map { case (finalState, uri) =>
      assertEquals(
        uri.renderString,
        "https://x-bitbucket-api-token-auth:user-api-token@bitbucket.example.com/scm/PROJ/repo.git"
      )
    }
  }
}
