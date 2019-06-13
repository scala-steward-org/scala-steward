package org.scalasteward.core.git

import org.http4s.Uri
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalatest.{FunSuite, Matchers}

class GitAlgTest extends FunSuite with Matchers {
  val repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString

  test("clone") {
    val url = Uri.uri("https://scala-steward@github.com/fthomas/datapackage")
    val state = gitAlg.clone(repo, url).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          config.workspace.toString,
          "git",
          "clone",
          "--recursive",
          "https://scala-steward@github.com/fthomas/datapackage",
          repoDir
        )
      ),
      extraEnv = Vector(
        List(("GIT_ASKPASS", config.gitAskPass.toString))
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
        List(repoDir, "git", "log", "--pretty=format:'%an'", "master..update/cats-1.0.0")
      ),
      extraEnv = Vector(
        List(("GIT_ASKPASS", config.gitAskPass.toString))
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
        List(repoDir, "git", "commit", "--all", "-m", "Initial commit", "--gpg-sign")
      ),
      extraEnv = Vector(
        List(("GIT_ASKPASS", config.gitAskPass.toString))
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
        List(repoDir, "git", "remote", "add", "upstream", "http://github.com/fthomas/datapackage"),
        List(repoDir, "git", "fetch", "upstream"),
        List(repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        List(repoDir, "git", "merge", "upstream/master"),
        List(repoDir, "git", "push", "--force", "--set-upstream", "origin", "master")
      ),
      extraEnv = Vector(
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString)),
        List(("GIT_ASKPASS", config.gitAskPass.toString))
      )
    )
  }
}
