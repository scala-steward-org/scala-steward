package eu.timepit.scalasteward.github.http4s

import cats.implicits._
import eu.timepit.scalasteward.git.Branch
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.github.http4s.http4sUrl._
import org.scalatest.{FunSuite, Matchers}

class http4SUrlTest extends FunSuite with Matchers {
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

  test("pulls") {
    pulls[Result](repo).map(_.toString) shouldBe
      Right("https://api.github.com/repos/fthomas/refined/pulls")
  }
}
