package eu.timepit.scalasteward.github.http4s

import cats.implicits._
import eu.timepit.scalasteward.git.Branch
import eu.timepit.scalasteward.github.data.Repo
import org.scalatest.{FunSuite, Matchers}

class http4sUrlTest extends FunSuite with Matchers {
  val http4sUrl = new Http4sUrl("https://api.github.com")
  import http4sUrl._

  type Result[A] = Either[Throwable, A]
  val repo = Repo("fthomas", "refined")
  val branch = Branch("master")

  test("branches") {
    branches[Result](repo, branch).map(_.toString) shouldBe
      Right("https://api.github.com/repos/fthomas/refined/branches/master")
  }

  test("forks") {
    forks[Result](repo).map(_.toString) shouldBe
      Right("https://api.github.com/repos/fthomas/refined/forks")
  }

  test("listPullRequests") {
    listPullRequests[Result](repo, "scala-steward:update/fs2-core-1.0.0").map(_.toString) shouldBe
      Right(
        "https://api.github.com/repos/fthomas/refined/pulls?head=scala-steward%3Aupdate/fs2-core-1.0.0&state=all"
      )
  }

  test("pulls") {
    pulls[Result](repo).map(_.toString) shouldBe
      Right("https://api.github.com/repos/fthomas/refined/pulls")
  }
}
