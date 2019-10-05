package org.scalasteward.core.vcs.data

import cats.effect.IO
import io.circe.parser
import org.http4s.Http4sLiteralSyntax
import org.scalasteward.core.git.Branch
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.io.Source

class RepoOutTest extends AnyFunSuite with Matchers {
  val parent =
    RepoOut(
      "base.g8",
      UserOut("ChristopherDavenport"),
      None,
      uri"https://github.com/ChristopherDavenport/base.g8.git",
      Branch("master")
    )

  val fork =
    RepoOut(
      "base.g8-1",
      UserOut("scala-steward"),
      Some(parent),
      uri"https://github.com/scala-steward/base.g8-1.git",
      Branch("master")
    )

  test("decode") {
    val input = Source.fromResource("create-fork.json").mkString
    parser.decode[RepoOut](input) shouldBe Right(fork)
  }

  test("parentOrRaise") {
    fork.parentOrRaise[IO].unsafeRunSync() shouldBe parent
  }

  test("repo") {
    fork.repo shouldBe Repo("scala-steward", "base.g8-1")
  }
}
