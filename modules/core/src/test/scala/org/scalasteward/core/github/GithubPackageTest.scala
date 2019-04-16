package org.scalasteward.core.github

import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext.config
import org.scalatest.{FunSuite, Matchers}

class GithubPackageTest extends FunSuite with Matchers {
  val repo = Repo("fthomas", "datapackage")

  test("github login for fork enabled configuration") {
    getLogin(config, repo) shouldBe config.gitHubLogin
  }

  test("github login for fork disabled configuration") {
    getLogin(config.copy(doNotFork = true), repo) shouldBe "fthomas"
  }
}
