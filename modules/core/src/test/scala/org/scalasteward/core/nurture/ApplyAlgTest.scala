package org.scalasteward.core.nurture

import munit.ScalaCheckSuite
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.data.{ProcessResult, RepoData, Update, UpdateData}

import better.files.File
import cats.Applicative
import cats.effect._
import cats.effect.concurrent.Ref
import org.http4s.HttpApp
import org.http4s.client.Client
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.{Config, Context}
import org.scalasteward.core.git.FileGitAlgTest.{master, Supplement}
import org.scalasteward.core.git.{Branch, Commit, FileGitAlg, GenGitAlg, Sha1}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.mock.MockContext
import org.scalasteward.core.mock.MockContext.{config, mockRoot}
import org.scalasteward.core.repocache._
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

class ApplyAlgTest extends ScalaCheckSuite {
  def step0(implicit
      CE: ConcurrentEffect[IO]
  ): Resource[IO, (ProcessAlg[IO], FileAlg[IO], WorkspaceAlg[IO], Context[IO])] = for {
    blocker <- Blocker[IO]
    config = Config.from(MockContext.args)
    implicit0(client: Client[IO]) = Client.fromHttpApp[IO](HttpApp.notFound)
    implicit0(fileAlg: FileAlg[IO]) = FileAlg.create[IO]
    _ <- Resource.eval(fileAlg.ensureExists(config.gitCfg.gitAskPass.parent))
    _ <- Resource.eval(
      fileAlg.writeFile(
        config.gitCfg.gitAskPass,
        """ echo bogus-password """
      )
    )
    _ <- Resource.eval(fileAlg.ensureExecutable(config.gitCfg.gitAskPass))
    implicit0(processAlg: ProcessAlg[IO]) = ProcessAlg.create[IO](blocker, config.processCfg)
    implicit0(workspaceAlg: WorkspaceAlg[IO]) = WorkspaceAlg.create[IO](config)
    context <- Resource.eval(Context.step1[IO](config))
  } yield (processAlg, fileAlg, workspaceAlg, context)

  def setupRepo(repo: Repo, identicalBranch: (Branch, Update.Single)): IO[Unit] =
    step0.use {
      case (
            implicit0(processAlg: ProcessAlg[IO]),
            implicit0(fileAlg: FileAlg[IO]),
            implicit0(workspaceAlg: WorkspaceAlg[IO]),
            context
          ) =>
        val (branch, update) = identicalBranch
        implicit val ioGitAlg: GenGitAlg[IO, File] =
          new FileGitAlg[IO](config.gitCfg).contramapRepoF(Applicative[IO].pure)
        val supplement = new Supplement[IO]
        val repoDir = mockRoot / "workspace" / "repos" / repo.owner / repo.repo
        for {
          _ <- supplement.createRepo(repoDir)
          _ <- fileAlg.writeFile(
            repoDir / "build.sbt",
            """libraryDependency += "foo" % "bar" % "1.2.3" """
          )
          _ <- supplement.git("add", "build.sbt")(repoDir)
          _ <- context.gitAlg.commitAll(repo, "Initial commit")
          // Create another simulated curated update branch with
          _ <- context.gitAlg.createBranch(repo, branch)
          _ <- fileAlg.writeFile(
            repoDir / "build.sbt",
            s"""libraryDependency += "foo" % "bar" % "${update.newerVersions.head}" """
          )
          _ <- supplement.git("add", "build.sbt")(repoDir)
          _ <- context.gitAlg.commitAll(repo, "Update bar to 1.2.4")
          _ <- context.gitAlg.checkoutBranch(repo, master)
        } yield ()
    }

  test("Ensure unique patchesets are pushed") {
    val firstBranch = Branch("update/foo-1.2.4")
    val duplicateBranch = Branch("update/foo-duplicate-1.2.4")
    val update = Update.Single("foo" % "bar" % "1.2.3", Nel.one("1.2.4"))
    val firstChangeset = (firstBranch, update)
    val res = ({
      def pushCommits(
          seenBranchesRef: Ref[IO, List[Branch]]
      ): (UpdateData, List[Commit]) => IO[ProcessResult] = { (data, _) =>
        for {
          _ <- seenBranchesRef.update(data.updateBranch :: _)
        } yield ProcessResult.Updated
      }

      val createPullRequest: UpdateData => IO[ProcessResult] = _ => IO.pure(ProcessResult.Updated)

      val repo = Repo("myorg", "myrepo")
      val fork = Repo("myfork", "myrepo")
      step0.use {
        case (
              implicit0(processAlg: ProcessAlg[IO]),
              implicit0(fileAlg: FileAlg[IO]),
              implicit0(workspaceAlg: WorkspaceAlg[IO]),
              context
            ) =>
          for {
            _ <- setupRepo(repo, firstChangeset)
            seenBranchesRef <- Ref[IO].of(List.empty[Branch])
            sha1 <- IO.fromEither(Sha1.from("adc83b19e793491b1c6ea0fd8b46cd9f32e592fc"))
            firstData = UpdateData(
              RepoData(
                repo,
                RepoCache(sha1, List.empty, Option.empty),
                RepoConfig()
              ),
              fork,
              update,
              master,
              sha1,
              Branch("bump")
            )
            secondData = firstData.copy(
              updateBranch = duplicateBranch,
              update = update
            )
            seenBranches <- seenBranchesRef.getAndUpdate(identity _)
            res1 <- context.applyAlg.applyNewUpdate(
              firstData,
              seenBranches,
              pushCommits(seenBranchesRef),
              createPullRequest
            )
            seenBranches <- seenBranchesRef.getAndUpdate(identity _)
            res2 <- context.applyAlg.applyNewUpdate(
              secondData,
              seenBranches,
              pushCommits(seenBranchesRef),
              createPullRequest
            )
          } yield (res1, res2)
      }
    }).unsafeRunSync()

    assertEquals(res, (ProcessResult.Updated, ProcessResult.Ignored))
  }

  test("Ensure non-unique patchesets are not pushed") {
    val branch = Branch("update/foo-1.2.4")
    val update = Update.Single("foo" % "bar" % "1.2.3", Nel.one("1.2.4"))
    val identicalBranch = (branch, update)
    val res = ({
      val pushCommits: (UpdateData, List[Commit]) => IO[ProcessResult] =
        (_, _) => IO.pure(ProcessResult.Updated)

      val createPullRequest: UpdateData => IO[ProcessResult] = _ => IO.pure(ProcessResult.Updated)

      val repo = Repo("myorg", "myrepo")
      val fork = Repo("myfork", "myrepo")
      step0.use {
        case (
              implicit0(processAlg: ProcessAlg[IO]),
              implicit0(fileAlg: FileAlg[IO]),
              implicit0(workspaceAlg: WorkspaceAlg[IO]),
              context
            ) =>
          for {
            _ <- setupRepo(repo, identicalBranch)
            seenBranchesRef <- Ref[IO].of(List(branch))
            sha1 <- IO.fromEither(Sha1.from("adc83b19e793491b1c6ea0fd8b46cd9f32e592fc"))
            data = UpdateData(
              RepoData(
                repo,
                RepoCache(sha1, List.empty, Option.empty),
                RepoConfig()
              ),
              fork,
              update,
              master,
              sha1,
              Branch("bump")
            )
            seenBranches <- seenBranchesRef.getAndUpdate(identity _)
            res <- context.applyAlg.applyNewUpdate(
              data,
              seenBranches,
              pushCommits,
              createPullRequest
            )
          } yield res
      }
    }).unsafeRunSync()

    assertEquals(res, ProcessResult.Ignored)
  }
}
