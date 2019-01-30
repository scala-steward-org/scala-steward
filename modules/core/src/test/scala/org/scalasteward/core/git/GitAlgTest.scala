package org.scalasteward.core.git

import cats.effect.IO
import org.http4s.Uri
import org.scalasteward.core.MockState.MockEff
import org.scalasteward.core.application.{Config, ConfigTest}
import org.scalasteward.core.github.data.{Repo, RepoOut, UserOut}
import org.scalasteward.core.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import org.scalasteward.core.{util, MockState}
import org.scalatest.{FunSuite, Matchers}

class GitAlgTest extends FunSuite with Matchers {
  implicit val config: Config = ConfigTest.dummyConfig
  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val processAlg: MockProcessAlg = new MockProcessAlg
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg
  val gitAlg: GitAlg[MockEff] = GitAlg.create
  val repo = Repo("fthomas", "datapackage")
  val parentRepoOut = RepoOut(
    "datapackage",
    UserOut("fthomas"),
    None,
    Uri.uri("https://fthomas@github.com/fthomas/datapackage"),
    Branch("master")
  )
  val childRepoOut = RepoOut(
    "datapackage",
    UserOut("scalasteward"),
    Some(parentRepoOut),
    Uri.uri("https://scala-steward@github.com/fthomas/datapackage"),
    Branch("master")
  )

  test("clone") {
    val url = util.uri
      .fromString[IO]("https://scala-steward@github.com/fthomas/datapackage")
      .unsafeRunSync()
    val state = gitAlg.clone(repo, url).runS(MockState.empty).unsafeRunSync()

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
      .unsafeRunSync()

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
      .unsafeRunSync()

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
      .unsafeRunSync()

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

  test("checkAndSyncFork should throw an exception when there is no parent fork is enabled") {
    gitAlg
      .checkAndSyncFork(repo, parentRepoOut)
      .runS(MockState.empty)
      .attempt
      .map(_.isLeft)
      .unsafeRunSync() shouldBe true
  }

  test("checkAndSyncFork should fork as usual when there is a parent") {
    val (state, result) = gitAlg
      .checkAndSyncFork(repo, childRepoOut)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          "git",
          "remote",
          "add",
          "upstream",
          s"https://@${config.gitHubLogin}github.com/fthomas/datapackage"
        ),
        List("git", "fetch", "upstream"),
        List("git", "checkout", "-B", "master", "--track", "upstream/master"),
        List("git", "merge", "upstream/master"),
        List("git", "push", "--force", "--set-upstream", "origin", "master")
      )
    )
    result shouldBe parentRepoOut
  }

  test("checkAndSyncFork should not fork when doNotFork is enabled") {
    implicit val config: Config = ConfigTest.dummyConfig.copy(doNotFork = true)
    val gitAlgWithoutForking = GitAlg.create
    val (state, repoOut) = gitAlgWithoutForking
      .checkAndSyncFork(repo, parentRepoOut)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty
    repoOut shouldBe parentRepoOut
  }
}
