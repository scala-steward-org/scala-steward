package org.scalasteward.core.git

import cats.effect.IO
import org.http4s.Uri
import org.scalasteward.core.MockState.MockEnv
import org.scalasteward.core.application.{Config, ConfigTest}
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import org.scalasteward.core.{util, MockState}
import org.scalatest.{FunSuite, Matchers}

class GitAlgTest extends FunSuite with Matchers {
  implicit val config: Config = ConfigTest.dummyConfig
  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val processAlg: MockProcessAlg = new MockProcessAlg
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg
  val gitAlg: GitAlg[MockEnv] = GitAlg.create
  val repo = Repo("fthomas", "datapackage")

  test("clone") {
    val url = util.uri
      .fromString[IO]("https://scala-steward@github.com/fthomas/datapackage")
      .unsafeRunSync()
    val state = gitAlg.clone(repo, url).runS(MockState.empty).value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          "git",
          "clone",
          "--recursive",
          "https://scala-steward@github.com/fthomas/datapackage",
          "/tmp/ws/fthomas/datapackage"
        )
      )
    )
  }

  test("branchAuthors") {
    val state = gitAlg
      .branchAuthors(repo, Branch("update/cats-1.0.0"), Branch("master"))
      .runS(MockState.empty)
      .value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("git", "log", "--pretty=format:'%an'", "master..update/cats-1.0.0")
      )
    )
  }

  test("commitAll") {
    val state = gitAlg
      .commitAll(repo, "Initial commit")
      .runS(MockState.empty)
      .value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("git", "commit", "--all", "-m", "Initial commit", "--gpg-sign")
      )
    )
  }

  test("syncFork") {
    val url = Uri.uri("http://github.com/fthomas/datapackage")
    val defaultBranch = Branch("master")

    val state = gitAlg
      .syncFork(repo, url, defaultBranch)
      .runS(MockState.empty)
      .value

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("git", "remote", "add", "upstream", "http://github.com/fthomas/datapackage"),
        List("git", "fetch", "upstream"),
        List("git", "checkout", "-B", "master", "--track", "upstream/master"),
        List("git", "merge", "upstream/master"),
        List("git", "push", "--force", "--set-upstream", "origin", "master")
      )
    )
  }
}
