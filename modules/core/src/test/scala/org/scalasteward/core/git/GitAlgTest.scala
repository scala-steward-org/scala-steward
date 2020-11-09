package org.scalasteward.core.git

import better.files.File
import cats.Monad
import cats.effect.IO
import cats.syntax.all._
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
  implicit val workspaceAlg: WorkspaceAlg[IO] = WorkspaceAlg.create[IO](config)
  implicit val ioGitAlg: GitAlg[IO] = GitAlg.create[IO](config)

  val repo: Repo = Repo("fthomas", "datapackage")
  val repoDir: String = (config.workspace / "fthomas/datapackage").toString
  val askPass = s"GIT_ASKPASS=${config.gitAskPass}"
  val envVars = List(askPass, "VAR1=val1", "VAR2=val2")

  test("clone") {
    val url = uri"https://scala-steward@github.com/fthomas/datapackage"
    val state = gitAlg.clone(repo, url).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        envVars ++ List(
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
        envVars ++ List(repoDir, "git", "log", "--pretty=format:'%an'", "master..update/cats-1.0.0")
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
        envVars ++ List(repoDir, "git", "commit", "--all", "--no-gpg-sign", "-m", "Initial commit")
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
        envVars ++ List(
          repoDir,
          "git",
          "remote",
          "add",
          "upstream",
          "http://github.com/fthomas/datapackage"
        ),
        envVars ++ List(repoDir, "git", "fetch", "--tags", "upstream", "master"),
        envVars ++ List(repoDir, "git", "checkout", "-B", "master", "--track", "upstream/master"),
        envVars ++ List(repoDir, "git", "merge", "upstream/master"),
        envVars ++ List(repoDir, "git", "push", "--force", "--set-upstream", "origin", "master")
      )
    )
  }

  test("containsChanges") {
    val repo = Repo("contains", "changes")
    val p = for {
      repoDir <- workspaceAlg.repoDir(repo)
      _ <- GitAlgTest.createRepo[IO](repo, repoDir)
      _ <- ioFileAlg.writeFile(repoDir / "test.txt", "hello")
      _ <- ioProcessAlg.exec(Nel.of("git", "add", "test.txt"), repoDir)
      _ <- ioGitAlg.commitAll(repo, "Add test.txt")
      c1 <- ioGitAlg.containsChanges(repo)
      _ <- ioFileAlg.writeFile(repoDir / "test.txt", "hello world")
      c2 <- ioGitAlg.containsChanges(repo)
      _ <- ioGitAlg.commitAllIfDirty(repo, "Modify test.txt")
      c3 <- ioGitAlg.containsChanges(repo)
    } yield (c1, c2, c3)
    p.unsafeRunSync() shouldBe ((false, true, false))
  }

  test("hasConflicts") {
    val repo = Repo("merge", "conflict")
    val p = for {
      repoDir <- workspaceAlg.repoDir(repo)
      _ <- GitAlgTest.createRepoWithConflict[IO](repo, repoDir)
      c1 <- ioGitAlg.hasConflicts(repo, Branch("conflicts-yes"), Branch("master"))
      c2 <- ioGitAlg.hasConflicts(repo, Branch("conflicts-no"), Branch("master"))
    } yield (c1, c2)
    p.unsafeRunSync() shouldBe ((true, false))
  }

  test("mergeTheirs") {
    val repo = Repo("merge", "theirs")
    val p = for {
      repoDir <- workspaceAlg.repoDir(repo)
      _ <- GitAlgTest.createRepoWithConflict[IO](repo, repoDir)
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

  test("mergeTheirs: CONFLICT (modify/delete)") {
    val repo = Repo("merge", "theirs2")
    val p = for {
      repoDir <- workspaceAlg.repoDir(repo)
      _ <- GitAlgTest.createRepoWithConflictFileRemovedOnMaster[IO](repo, repoDir)
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
  def createRepo[F[_]](repo: Repo, repoDir: File)(implicit
      fileAlg: FileAlg[F],
      gitAlg: GitAlg[F],
      processAlg: ProcessAlg[F],
      F: Monad[F]
  ): F[Unit] =
    for {
      _ <- gitAlg.removeClone(repo)
      _ <- fileAlg.ensureExists(repoDir)
      _ <- processAlg.exec(Nel.of("git", "init", "."), repoDir)
      _ <- gitAlg.setAuthor(repo, config.gitAuthor)
    } yield ()

  def createRepoWithConflict[F[_]](repo: Repo, repoDir: File)(implicit
      fileAlg: FileAlg[F],
      gitAlg: GitAlg[F],
      processAlg: ProcessAlg[F],
      F: Monad[F]
  ): F[Unit] =
    for {
      _ <- createRepo[F](repo, repoDir)
      // work on master
      _ <- fileAlg.writeFile(repoDir / "file1", "file1, line1")
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1")
      _ <- processAlg.exec(Nel.of("git", "add", "file1"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Initial commit"), repoDir)
      // work on conflicts-no
      _ <- gitAlg.createBranch(repo, Branch("conflicts-no"))
      _ <- fileAlg.writeFile(repoDir / "file3", "file3, line1")
      _ <- processAlg.exec(Nel.of("git", "add", "file3"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Add file3 on conflicts-no"), repoDir)
      _ <- gitAlg.checkoutBranch(repo, Branch("master"))
      // work on conflicts-yes
      _ <- gitAlg.createBranch(repo, Branch("conflicts-yes"))
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1\nfile2, line2 on conflicts-yes")
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Modify file2 on conflicts-yes"), repoDir)
      _ <- gitAlg.checkoutBranch(repo, Branch("master"))
      // work on master
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1\nfile2, line2 on master")
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Modify file2 on master"), repoDir)
    } yield ()

  def createRepoWithConflictFileRemovedOnMaster[F[_]](repo: Repo, repoDir: File)(implicit
      fileAlg: FileAlg[F],
      gitAlg: GitAlg[F],
      processAlg: ProcessAlg[F],
      F: Monad[F]
  ): F[Unit] =
    for {
      _ <- createRepo[F](repo, repoDir)
      // work on master
      _ <- fileAlg.writeFile(repoDir / "file1", "file1, line1")
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1")
      _ <- processAlg.exec(Nel.of("git", "add", "file1"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Initial commit"), repoDir)
      // work on conflicts-yes
      _ <- gitAlg.createBranch(repo, Branch("conflicts-yes"))
      _ <- fileAlg.writeFile(repoDir / "file2", "file2, line1\nfile2, line2 on conflicts-yes")
      _ <- processAlg.exec(Nel.of("git", "add", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Modify file2 on conflicts-yes"), repoDir)
      _ <- gitAlg.checkoutBranch(repo, Branch("master"))
      // work on master
      _ <- processAlg.exec(Nel.of("git", "rm", "file2"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "add", "-A"), repoDir)
      _ <- processAlg.exec(Nel.of("git", "commit", "-m", "Remove file2 on master"), repoDir)
    } yield ()
}
