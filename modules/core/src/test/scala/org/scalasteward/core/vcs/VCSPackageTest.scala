package org.scalasteward.core.vcs

import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.{FunSuite, Matchers}

class VCSPackageTest extends FunSuite with Matchers {
  val repo = Repo("fthomas", "datapackage")

  test("github login for fork enabled configuration") {
    getLogin(config, repo) shouldBe config.vcsLogin
  }

  test("github login for fork disabled configuration") {
    getLogin(config.copy(doNotFork = true), repo) shouldBe "fthomas"
  }
}
