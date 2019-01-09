package org.scalasteward.core.github
import org.scalatest.{FunSuite, Matchers}
import org.scalasteward.core.application.ConfigTest
import org.scalasteward.core.github.data.Repo

class GithubPackageTest extends FunSuite with Matchers {

  val repo = Repo("fthomas", "datapackage")

  test("github login for fork enabled configuration") {
    getLogin(ConfigTest.dummyConfig, repo) shouldBe ""
  }

  test("github login for fork disabled configuration") {
    val configWithForkEnabled = ConfigTest.dummyConfig.copy(doNotFork = true)
    getLogin(configWithForkEnabled, repo) shouldBe "fthomas"
  }
}
