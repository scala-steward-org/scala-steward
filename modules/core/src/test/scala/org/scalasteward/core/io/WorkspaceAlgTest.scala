package org.scalasteward.core.io

import cats.FlatMap
import cats.effect.IO
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.io.WorkspaceAlgTest._
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class WorkspaceAlgTest extends AnyFunSuite with Matchers {
  def findProjects(projectDirs: List[String], repoName: String) =
    ioWorkspaceAlg(projectDirs)
      .findProjects(Repo("owner", repoName))
      .unsafeRunSync()

  val rootName = "repo-root"
  val subName = "repo-sub"
  val subSubName = "repo-sub-sub"
  val subWithRootName = "repo-sub-with-root"
  val notAProjectName = "repo-without-project"

  test("findProjects without projectDirs defined, returns only project in root") {
    config.workspace.toTemporary { _ =>
      setupReposIncludingProjects()
      val projectDirs = List.empty
      val repos = config.workspace / "repos"
      findProjects(projectDirs, rootName) should contain only repos / "owner" / rootName
      findProjects(projectDirs, subName) should contain only repos / "owner" / subName
      findProjects(projectDirs, subSubName) should contain only repos / "owner" / subSubName
      findProjects(projectDirs, subWithRootName) should contain only repos / "owner" / subWithRootName
      findProjects(projectDirs, notAProjectName) should contain only repos / "owner" / notAProjectName
    }
  }

  test("findProjects */* (in root of all repos)") {
    config.workspace.toTemporary { _ =>
      val (root, _, _, _, subWithRoot) = setupReposIncludingProjects()
      val projectDirs = List("*/*")
      findProjects(projectDirs, rootName) should contain only root
      findProjects(projectDirs, subName) should have size 0
      findProjects(projectDirs, subSubName) should have size 0
      findProjects(projectDirs, subWithRootName) should contain only subWithRoot.parent
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  test("findProjects */*/* (in direct sub directories of all repos)") {
    config.workspace.toTemporary { _ =>
      val (_, sub, _, _, subWithRoot) = setupReposIncludingProjects()
      val projectDirs = List("*/*/*")
      findProjects(projectDirs, rootName) should have size 0
      findProjects(projectDirs, subName) should contain only sub
      findProjects(projectDirs, subSubName) should have size 0
      findProjects(projectDirs, subWithRootName) should contain only subWithRoot
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  test("findProjects */*/** (in all sub directories of all repos)") {
    config.workspace.toTemporary { _ =>
      val (_, sub, subSub1, subSub2, subWithRoot) = setupReposIncludingProjects()
      val projectDirs = List("*/*/**")
      findProjects(projectDirs, rootName) should have size 0
      findProjects(projectDirs, subName) should contain only sub
      findProjects(projectDirs, subSubName) should contain theSameElementsAs List(
        subSub1,
        subSub2
      )
      findProjects(projectDirs, subWithRootName) should contain only subWithRoot
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  test("findProjects */** (in all directories of all repos)") {
    config.workspace.toTemporary { _ =>
      val (root, sub, subSub1, subSub2, subWithRoot) = setupReposIncludingProjects()
      val projectDirs = List("*/**")
      findProjects(projectDirs, rootName) should contain only root
      findProjects(projectDirs, subName) should contain only sub
      findProjects(projectDirs, subSubName) should contain theSameElementsAs List(
        subSub1,
        subSub2
      )
      findProjects(projectDirs, subWithRootName) should contain theSameElementsAs List(
        subWithRoot,
        subWithRoot.parent
      )
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  test("findProjects ** (in all directories of all repos)") {
    config.workspace.toTemporary { _ =>
      val (root, sub, subSub1, subSub2, subWithRoot) = setupReposIncludingProjects()
      val projectDirs = List("**")
      findProjects(projectDirs, rootName) should contain only root
      findProjects(projectDirs, subName) should contain only sub
      findProjects(projectDirs, subSubName) should contain theSameElementsAs List(
        subSub1,
        subSub2
      )
      findProjects(projectDirs, subWithRootName) should contain theSameElementsAs List(
        subWithRoot,
        subWithRoot.parent
      )
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  test(s"findProjects in owner/$subSubName/sub1/** returns only sub1/app") {
    config.workspace.toTemporary { _ =>
      val (_, _, subSub1, _, _) = setupReposIncludingProjects()
      val projectDirs = List(s"owner/$subSubName/sub1/**")
      findProjects(projectDirs, rootName) should have size 0
      findProjects(projectDirs, subName) should have size 0
      findProjects(projectDirs, subSubName) should contain only subSub1
      findProjects(projectDirs, subWithRootName) should have size 0
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  test(s"findProjects in owner/$subSubName/**/app returns sub1/app and sub2/app") {
    config.workspace.toTemporary { _ =>
      val (_, _, subSub1, subSub2, _) = setupReposIncludingProjects()
      val projectDirs = List(s"owner/$subSubName/**/app")
      findProjects(projectDirs, rootName) should have size 0
      findProjects(projectDirs, subName) should have size 0
      findProjects(projectDirs, subSubName) should contain theSameElementsAs List(
        subSub1,
        subSub2
      )
      findProjects(projectDirs, subWithRootName) should have size 0
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  test(s"findProjects * does not find anything as it is on repo name level") {
    config.workspace.toTemporary { _ =>
      val (_, _, _, _, _) = setupReposIncludingProjects()
      val projectDirs = List(s"*")
      findProjects(projectDirs, rootName) should have size 0
      findProjects(projectDirs, subName) should have size 0
      findProjects(projectDirs, subSubName) should have size 0
      findProjects(projectDirs, subWithRootName) should have size 0
      findProjects(projectDirs, notAProjectName) should have size 0
    }
  }

  private def setupReposIncludingProjects() = {
    val repos = config.workspace / "repos"
    val root = (repos / "owner" / rootName).createDirectories()
    val sub = (repos / "owner" / subName / "sub").createDirectories()
    val subSub1 = (repos / "owner" / subSubName / "sub1" / "app").createDirectories()
    val subSub2 = (repos / "owner" / subSubName / "sub2" / "app").createDirectories()
    val subWithRoot = (repos / "owner" / subWithRootName / "sub").createDirectories()
    val notAProject = (repos / "owner" / notAProjectName).createDirectories()
    val unrelatedOwner = (repos / "anotherOwner" / rootName).createDirectories()
    List(root, sub, subSub1, subSub2, subWithRoot, subWithRoot.parent, unrelatedOwner)
      .foreach(f => (f / "build.sbt").createFile())
    (notAProject / "not-build.sbt").createFile()
    (root, sub, subSub1, subSub2, subWithRoot)
  }
}

object WorkspaceAlgTest {
  def ioWorkspaceAlg(projectDirs: List[String])(
      implicit
      fileAlg: FileAlg[IO],
      logger: Logger[IO],
      config: Config,
      F: FlatMap[IO]
  ): WorkspaceAlg[IO] =
    WorkspaceAlg.create[IO](fileAlg, logger, config.copy(projectDirs = projectDirs), F)
}
