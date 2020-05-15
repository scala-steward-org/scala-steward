package org.scalasteward.core.git

import better.files.File
import cats.Monad
import cats.effect.IO
import cats.implicits._
import org.http4s.syntax.literals._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.io.ProcessAlgTest.ioProcessAlg
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GitAlgTest extends AnyFunSuite with Matchers {
  implicit val workspaceAlg: WorkspaceAlg[IO] = WorkspaceAlg.create[IO]
  val ioGitAlg: GitAlg[IO] = GitAlg.create[IO]

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
        List(askPass, repoDir, "git", "commit", "--all", "-m", "Initial commit", "--no-gpg-sign")
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
        List(askPass, repoDir, "git", "fetch", "--tags", "upstream", "master"),
        List(askPass, repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        List(askPass, repoDir, "git", "merge", "upstream/master"),
        List(askPass, repoDir, "git", "push", "--force", "--set-upstream", "origin", "master")
      )
    )
  }

  test("hasConflicts") {
    val repo = Repo("merge", "conflict")
    val p = for {
      repoDir <- workspaceAlg.repoDir(repo)
      _ <- GitAlgTest.createGitRepoWithConflict[IO](repoDir)
      c1 <- ioGitAlg.hasConflicts(repo, Branch("conflicts-yes"), Branch("master"))
      c2 <- ioGitAlg.hasConflicts(repo, Branch("conflicts-no"), Branch("master"))
    } yield (c1, c2)
    p.unsafeRunSync() shouldBe ((true, false))
  }

  test("mergeTheirs") {
    val repo = Repo("merge", "theirs")
    val p = for {
      repoDir <- workspaceAlg.repoDir(repo)
      _ <- GitAlgTest.createGitRepoWithConflict[IO](repoDir)
      master = Branch("master")
      branch = Branch("conflicts-yes")
      c1 <- ioGitAlg.hasConflicts(repo, branch, master)
      m1 <- ioGitAlg.isMerged(repo, master, branch)
      _ <- ioGitAlg.checkoutBranch(repo, branch)
      _ <- ioGitAlg.mergeTheirs(repo, master)
      c2 <- ioGitAlg.hasConflicts(repo, branch, master)
      m2 <- ioGitAlg.isMerged(repo, master, branch)
    } yield (c1, m1, c2, m2)
    p.unsafeRunSync() shouldBe ((true, false, false, true))
  }
}

object GitAlgTest {
  def createGitRepoWithConflict[F[_]](repoDir: File)(implicit
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      F: Monad[F]
  ): F[Unit] =
    for {
      _ <- fileAlg.deleteForce(repoDir)
      _ <- fileAlg.ensureExists(repoDir)
      _ <- processAlg.exec(Nel.of("git", "init", "."), repoDir)
      // work on master
      _ <- fileAlg.writeFile(repoDir / "file1", "file1, line1")
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1")
      _ <- processAlg.exec(Nel.of("git", "add", "file1"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Initial commit"), repoDir)
      // work on conflicts-no
      _ <- processAlg.exec(Nel.of("git", "checkout", "-b", "conflicts-no"), repoDir)
      _ <- fileAlg.writeFile(repoDir / "file3", "file3, line1")
      _ <- processAlg.exec(Nel.of("git", "add", "file3"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Add file3 on conflicts-no"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "checkout", "master"), repoDir)
      // work on conflicts-yes
      _ <- processAlg.exec(Nel.of("git", "checkout", "-b", "conflicts-yes"), repoDir)
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1\nfile2, line2 on conflicts-yes")
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Modify file2 on conflicts-yes"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "checkout", "master"), repoDir)
      // work on master
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1\nfile2, line2 on master")
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Modify file2 on master"), repoDir)
    } yield ()
}
