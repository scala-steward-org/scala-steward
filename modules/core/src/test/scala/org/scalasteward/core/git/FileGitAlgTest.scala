package org.scalasteward.core.git

import better.files.File
import cats.Monad
import cats.effect.IO
import cats.syntax.all._
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.git.FileGitAlgTest.{master, Supplement}
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.io.ProcessAlgTest.ioProcessAlg
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.mock.MockContext._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FileGitAlgTest extends AnyFunSuite with Matchers {
  implicit private val ioWorkspaceAlg: WorkspaceAlg[IO] = WorkspaceAlg.create[IO](config)
  implicit private val ioFileGitAlg: FileGitAlg[IO] = new FileGitAlg[IO](config)
  private val supplement = new Supplement[IO]
  private val rootDir = File.temp / "scala-steward" / "git-tests"

  test("containsChanges") {
    val repo = rootDir / "containsChanges"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- ioFileAlg.writeFile(repo / "test.txt", "hello")
      _ <- ioFileGitAlg.git("add", "test.txt")(repo)
      _ <- ioFileGitAlg.commitAll(repo, "Add test.txt")
      c1 <- ioFileGitAlg.containsChanges(repo)
      _ <- ioFileAlg.writeFile(repo / "test.txt", "hello world")
      c2 <- ioFileGitAlg.containsChanges(repo)
      _ <- ioFileGitAlg.commitAllIfDirty(repo, "Modify test.txt")
      c3 <- ioFileGitAlg.containsChanges(repo)
    } yield (c1, c2, c3)
    p.unsafeRunSync() shouldBe ((false, true, false))
  }

  test("hasConflicts") {
    val repo = rootDir / "hasConflicts"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflict(repo)
      c1 <- ioFileGitAlg.hasConflicts(repo, Branch("conflicts-yes"), master)
      c2 <- ioFileGitAlg.hasConflicts(repo, Branch("conflicts-no"), master)
    } yield (c1, c2)
    p.unsafeRunSync() shouldBe ((true, false))
  }

  test("mergeTheirs") {
    val repo = rootDir / "mergeTheirs"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflict(repo)
      branch = Branch("conflicts-yes")
      c1 <- ioFileGitAlg.hasConflicts(repo, branch, master)
      m1 <- ioFileGitAlg.isMerged(repo, master, branch)
      _ <- ioFileGitAlg.checkoutBranch(repo, branch)
      _ <- ioFileGitAlg.mergeTheirs(repo, master)
      c2 <- ioFileGitAlg.hasConflicts(repo, branch, master)
      m2 <- ioFileGitAlg.isMerged(repo, master, branch)
    } yield (c1, m1, c2, m2)
    p.unsafeRunSync() shouldBe ((true, false, false, true))
  }

  test("mergeTheirs: CONFLICT (modify/delete)") {
    val repo = rootDir / "mergeTheirs-modify-delete"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflictFileRemovedOnMaster(repo)
      branch = Branch("conflicts-yes")
      c1 <- ioFileGitAlg.hasConflicts(repo, branch, master)
      m1 <- ioFileGitAlg.isMerged(repo, master, branch)
      _ <- ioFileGitAlg.checkoutBranch(repo, branch)
      _ <- ioFileGitAlg.mergeTheirs(repo, master)
      c2 <- ioFileGitAlg.hasConflicts(repo, branch, master)
      m2 <- ioFileGitAlg.isMerged(repo, master, branch)
    } yield (c1, m1, c2, m2)
    p.unsafeRunSync() shouldBe ((true, false, false, true))
  }
}

object FileGitAlgTest {
  val master: Branch = Branch("master")

  final class Supplement[F[_]](implicit
      fileAlg: FileAlg[F],
      fileGitAlg: FileGitAlg[F],
      F: Monad[F]
  ) {
    def createRepo(repo: File): F[Unit] =
      for {
        _ <- fileGitAlg.removeClone(repo)
        _ <- fileAlg.ensureExists(repo)
        _ <- fileGitAlg.git("init", ".")(repo)
        _ <- fileGitAlg.setAuthor(repo, config.gitAuthor)
      } yield ()

    def createConflict(repo: File): F[Unit] =
      for {
        // work on master
        _ <- fileAlg.writeFile(repo / "file1", "file1, line1")
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1")
        _ <- fileGitAlg.git("add", "file1")(repo)
        _ <- fileGitAlg.git("add", "file2")(repo)
        _ <- fileGitAlg.commitAll(repo, "Initial commit")
        // work on conflicts-no
        _ <- fileGitAlg.createBranch(repo, Branch("conflicts-no"))
        _ <- fileAlg.writeFile(repo / "file3", "file3, line1")
        _ <- fileGitAlg.git("add", "file3")(repo)
        _ <- fileGitAlg.commitAll(repo, "Add file3 on conflicts-no")
        _ <- fileGitAlg.checkoutBranch(repo, master)
        // work on conflicts-yes
        _ <- fileGitAlg.createBranch(repo, Branch("conflicts-yes"))
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1\nfile2, line2 on conflicts-yes")
        _ <- fileGitAlg.git("add", "file2")(repo)
        _ <- fileGitAlg.commitAll(repo, "Modify file2 on conflicts-yes")
        _ <- fileGitAlg.checkoutBranch(repo, master)
        // work on master
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1\nfile2, line2 on master")
        _ <- fileGitAlg.git("add", "file2")(repo)
        _ <- fileGitAlg.commitAll(repo, "Modify file2 on master")
      } yield ()

    def createConflictFileRemovedOnMaster(repo: File): F[Unit] =
      for {
        // work on master
        _ <- fileAlg.writeFile(repo / "file1", "file1, line1")
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1")
        _ <- fileGitAlg.git("add", "file1")(repo)
        _ <- fileGitAlg.git("add", "file2")(repo)
        _ <- fileGitAlg.commitAll(repo, "Initial commit")
        // work on conflicts-yes
        _ <- fileGitAlg.createBranch(repo, Branch("conflicts-yes"))
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1\nfile2, line2 on conflicts-yes")
        _ <- fileGitAlg.git("add", "file2")(repo)
        _ <- fileGitAlg.commitAll(repo, "Modify file2 on conflicts-yes")
        _ <- fileGitAlg.checkoutBranch(repo, master)
        // work on master
        _ <- fileGitAlg.git("rm", "file2")(repo)
        _ <- fileGitAlg.git("add", "-A")(repo)
        _ <- fileGitAlg.commitAll(repo, "Remove file2 on master")
      } yield ()
  }
}
