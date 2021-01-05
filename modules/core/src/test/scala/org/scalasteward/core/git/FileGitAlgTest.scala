package org.scalasteward.core.git

import better.files.File
import cats.Monad
import cats.effect.IO
import cats.syntax.all._
import munit.FunSuite
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.git.FileGitAlgTest.{master, Supplement}
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.io.ProcessAlgTest.ioProcessAlg
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.util.Nel

class FileGitAlgTest extends FunSuite {
  implicit private val ioWorkspaceAlg: WorkspaceAlg[IO] = WorkspaceAlg.create[IO](config)
  implicit private val ioGitAlg: GenGitAlg[IO, File] =
    new FileGitAlg[IO](config.gitCfg).contramapRepoF(IO.pure)
  private val supplement = new Supplement[IO]
  private val rootDir = File.temp / "scala-steward" / "git-tests"

  test("branchAuthors") {
    val repo = rootDir / "branchAuthors"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflict(repo)
      authors <- ioGitAlg.branchAuthors(repo, Branch("conflicts-no"), master)
    } yield authors
    assertEquals(p.unsafeRunSync(), List("'Bot Doe'"))
  }

  test("cloneExists") {
    val repo = rootDir / "cloneExists"
    val p = for {
      e1 <- ioGitAlg.cloneExists(repo)
      _ <- supplement.createRepo(repo)
      e2 <- ioGitAlg.cloneExists(repo)
      _ <- ioGitAlg.removeClone(repo)
      e3 <- ioGitAlg.cloneExists(repo)
    } yield (e1, e2, e3)
    assertEquals(p.unsafeRunSync(), (false, true, false))
  }

  test("containsChanges") {
    val repo = rootDir / "containsChanges"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- ioFileAlg.writeFile(repo / "test.txt", "hello")
      _ <- supplement.git("add", "test.txt")(repo)
      _ <- ioGitAlg.commitAll(repo, "Add test.txt")
      c1 <- ioGitAlg.containsChanges(repo)
      _ <- ioFileAlg.writeFile(repo / "test.txt", "hello world")
      c2 <- ioGitAlg.containsChanges(repo)
      _ <- ioGitAlg.commitAllIfDirty(repo, "Modify test.txt")
      c3 <- ioGitAlg.containsChanges(repo)
    } yield (c1, c2, c3)
    assertEquals(p.unsafeRunSync(), (false, true, false))
  }

  test("currentBranch") {
    val repo = rootDir / "currentBranch"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.git("commit", "--allow-empty", "-m", "Initial commit")(repo)
      branch <- ioGitAlg.currentBranch(repo)
      _ <- ioGitAlg.latestSha1(repo, branch)
    } yield branch
    assertEquals(p.unsafeRunSync(), master)
  }

  test("discardChanges") {
    val repo = rootDir / "discardChanges"
    val p = for {
      _ <- supplement.createRepo(repo)
      file = repo / "test.txt"
      _ <- ioFileAlg.writeFile(file, "hello")
      _ <- supplement.git("add", "test.txt")(repo)
      _ <- ioGitAlg.commitAll(repo, "Add test.txt")
      _ <- ioFileAlg.writeFile(file, "world")
      before <- ioFileAlg.readFile(file)
      _ <- ioGitAlg.discardChanges(repo)
      after <- ioFileAlg.readFile(file)
    } yield (before, after)
    val (before, after) = p.unsafeRunSync()
    assertEquals(before, Some("world"))
    assertEquals(after, Some("hello"))
  }

  test("findFilesContaining") {
    val repo = rootDir / "findFilesContaining"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflict(repo)
      files <- ioGitAlg.findFilesContaining(repo, "line1")
    } yield files
    assertEquals(p.unsafeRunSync(), List("file1", "file2"))
  }

  test("hasConflicts") {
    val repo = rootDir / "hasConflicts"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflict(repo)
      c1 <- ioGitAlg.hasConflicts(repo, Branch("conflicts-yes"), master)
      c2 <- ioGitAlg.hasConflicts(repo, Branch("conflicts-no"), master)
    } yield (c1, c2)
    assertEquals(p.unsafeRunSync(), (true, false))
  }

  test("mergeTheirs") {
    val repo = rootDir / "mergeTheirs"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflict(repo)
      branch = Branch("conflicts-yes")
      c1 <- ioGitAlg.hasConflicts(repo, branch, master)
      m1 <- ioGitAlg.isMerged(repo, master, branch)
      _ <- ioGitAlg.checkoutBranch(repo, branch)
      _ <- ioGitAlg.mergeTheirs(repo, master)
      c2 <- ioGitAlg.hasConflicts(repo, branch, master)
      m2 <- ioGitAlg.isMerged(repo, master, branch)
    } yield (c1, m1, c2, m2)
    assertEquals(p.unsafeRunSync(), (true, false, false, true))
  }

  test("mergeTheirs: CONFLICT (modify/delete)") {
    val repo = rootDir / "mergeTheirs-modify-delete"
    val p = for {
      _ <- supplement.createRepo(repo)
      _ <- supplement.createConflictFileRemovedOnMaster(repo)
      branch = Branch("conflicts-yes")
      c1 <- ioGitAlg.hasConflicts(repo, branch, master)
      m1 <- ioGitAlg.isMerged(repo, master, branch)
      _ <- ioGitAlg.checkoutBranch(repo, branch)
      _ <- ioGitAlg.mergeTheirs(repo, master)
      c2 <- ioGitAlg.hasConflicts(repo, branch, master)
      m2 <- ioGitAlg.isMerged(repo, master, branch)
    } yield (c1, m1, c2, m2)
    assertEquals(p.unsafeRunSync(), (true, false, false, true))
  }

  test("version") {
    assert(ioGitAlg.version.unsafeRunSync().nonEmpty)
  }
}

object FileGitAlgTest {
  val master: Branch = Branch("master")

  final class Supplement[F[_]](implicit
      fileAlg: FileAlg[F],
      gitAlg: GenGitAlg[F, File],
      processAlg: ProcessAlg[F],
      F: Monad[F]
  ) {
    def git(args: String*)(repo: File): F[Unit] =
      processAlg.exec(Nel.of("git", args: _*), repo).void

    def createRepo(repo: File): F[Unit] =
      for {
        _ <- gitAlg.removeClone(repo)
        _ <- fileAlg.ensureExists(repo)
        _ <- git("init", ".")(repo)
        _ <- gitAlg.setAuthor(repo, config.gitCfg.gitAuthor)
      } yield ()

    def createConflict(repo: File): F[Unit] =
      for {
        // work on master
        _ <- fileAlg.writeFile(repo / "file1", "file1, line1")
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1")
        _ <- git("add", "file1")(repo)
        _ <- git("add", "file2")(repo)
        _ <- gitAlg.commitAll(repo, "Initial commit")
        // work on conflicts-no
        _ <- gitAlg.createBranch(repo, Branch("conflicts-no"))
        _ <- fileAlg.writeFile(repo / "file3", "file3, line1")
        _ <- git("add", "file3")(repo)
        _ <- gitAlg.commitAll(repo, "Add file3 on conflicts-no")
        _ <- gitAlg.checkoutBranch(repo, master)
        // work on conflicts-yes
        _ <- gitAlg.createBranch(repo, Branch("conflicts-yes"))
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1\nfile2, line2 on conflicts-yes")
        _ <- git("add", "file2")(repo)
        _ <- gitAlg.commitAll(repo, "Modify file2 on conflicts-yes")
        _ <- gitAlg.checkoutBranch(repo, master)
        // work on master
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1\nfile2, line2 on master")
        _ <- git("add", "file2")(repo)
        _ <- gitAlg.commitAll(repo, "Modify file2 on master")
      } yield ()

    def createConflictFileRemovedOnMaster(repo: File): F[Unit] =
      for {
        // work on master
        _ <- fileAlg.writeFile(repo / "file1", "file1, line1")
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1")
        _ <- git("add", "file1")(repo)
        _ <- git("add", "file2")(repo)
        _ <- gitAlg.commitAll(repo, "Initial commit")
        // work on conflicts-yes
        _ <- gitAlg.createBranch(repo, Branch("conflicts-yes"))
        _ <- fileAlg.writeFile(repo / "file2", "file2, line1\nfile2, line2 on conflicts-yes")
        _ <- git("add", "file2")(repo)
        _ <- gitAlg.commitAll(repo, "Modify file2 on conflicts-yes")
        _ <- gitAlg.checkoutBranch(repo, master)
        // work on master
        _ <- git("rm", "file2")(repo)
        _ <- git("add", "-A")(repo)
        _ <- gitAlg.commitAll(repo, "Remove file2 on master")
      } yield ()
  }
}
