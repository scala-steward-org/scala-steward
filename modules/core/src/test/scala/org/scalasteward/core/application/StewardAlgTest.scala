package org.scalasteward.core.application

import org.scalatest.{FunSuite, Matchers}
import better.files.File
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext._
import org.mockito.Mockito._
import org.scalasteward.core.nurture.{NurtureAlg, PullRequestRepository}
import org.scalasteward.core.repocache.RepoCacheAlg
import org.scalasteward.core.update.UpdateService
import org.scalasteward.core.vcs.{VCSApiAlg, VCSExtraAlg}

class StewardAlgTest extends FunSuite with Matchers {

  implicit val vcsMock = mock(classOf[VCSApiAlg[MockEff]])
  implicit val vcsExtraMock = mock(classOf[VCSExtraAlg[MockEff]])
  implicit val pullrequestMock = mock(classOf[PullRequestRepository[MockEff]])
  implicit val updateServiceMock = mock(classOf[UpdateService[MockEff]])
  implicit val repocacheAlgMock = mock(classOf[RepoCacheAlg[MockEff]])
  implicit val nurtureAlgMock = mock(classOf[NurtureAlg[MockEff]])

  implicit val conf = config.copy(vcsType = SupportedVCS.Gitlab)
  val algTest = new StewardAlg[MockEff]()(
    conf,
    fileAlg,
    logAlg,
    logger,
    nurtureAlgMock,
    repocacheAlgMock,
    sbtAlg,
    updateServiceMock,
    workspaceAlg,
    mockEffBracketThrowable
  )

  test("the StewardAlg read the repos.md file like ") {
    val f = File.temp / "repos_test.md"
    val repos = algTest
      .readRepos(f)
      .runA(MockState.empty.add(f, "- HCScorps/baton\n- HCScorps/SOA/pid:344"))
      .unsafeRunSync()

    repos shouldEqual List(Repo("HCScorps", "baton"), Repo("HCScorps", "SOA", Some(344)))
  }

}
