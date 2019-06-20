package org.scalasteward.core.git

import org.http4s.Http4sLiteralSyntax
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalatest.{FunSuite, Matchers}

class GitAlgTest extends FunSuite with Matchers {
  val repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString
  val askPass = s"GIT_ASKPASS=${config.gitAskPass}"

  test("clone") {
    val url = uri"https://scala-steward@github.com/fthomas/datapackage"
    val state = gitAlg.clone(repo, url).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          askPass,
          config.workspace.toString,
          "git",
          "clone",
          "--recursive",
          "https://scala-steward@github.com/fthomas/datapackage",
          repoDir
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
        List(askPass, repoDir, "git", "log", "--pretty=format:'%an'", "master..update/cats-1.0.0")
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
        List(askPass, repoDir, "git", "commit", "--all", "-m", "Initial commit", "--gpg-sign")
      )
    )
  }

  test("syncFork") {
    val url = uri"http://github.com/fthomas/datapackage"
    val defaultBranch = Branch("master")

    val state = gitAlg
      .syncFork(repo, url, defaultBranch)
      .runS(MockState.empty)
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          askPass,
          repoDir,
          "git",
          "remote",
          "add",
          "upstream",
          "http://github.com/fthomas/datapackage"
        ),
        List(askPass, repoDir, "git", "fetch", "upstream"),
        List(askPass, repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        List(askPass, repoDir, "git", "merge", "upstream/master"),
        List(askPass, repoDir, "git", "push", "--force", "--set-upstream", "origin", "master")
      )
    )
  }
}
