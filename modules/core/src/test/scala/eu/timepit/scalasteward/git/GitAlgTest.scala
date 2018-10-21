package eu.timepit.scalasteward.git

import eu.timepit.scalasteward.MockState
import eu.timepit.scalasteward.MockState.MockEnv
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import org.http4s.Uri
import org.scalatest.{FunSuite, Matchers}

class GitAlgTest extends FunSuite with Matchers {
  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val processAlg: MockProcessAlg = new MockProcessAlg
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg
  val gitAlg: GitAlg[MockEnv] = GitAlg.create

  test("syncFork") {
    val repo = Repo("fthomas", "datapackage")
    val url = Uri.uri("http://github.com/fthomas/datapackage")
    val defaultBranch = Branch("master")

    val state = gitAlg
      .syncFork(repo, url, defaultBranch)
      .runS(MockState.empty)
      .value

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
}
