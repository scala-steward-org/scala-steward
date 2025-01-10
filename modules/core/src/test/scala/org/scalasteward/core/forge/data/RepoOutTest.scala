package org.scalasteward.core.forge.data

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser
import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import scala.io.Source

class RepoOutTest extends FunSuite {
  private val parent =
    RepoOut(
      "base.g8",
      UserOut("ChristopherDavenport"),
      None,
      uri"https://github.com/ChristopherDavenport/base.g8.git",
      Branch("master")
    )

  private val fork =
    RepoOut(
      "base.g8-1",
      UserOut("scala-steward"),
      Some(parent),
      uri"https://github.com/scala-steward/base.g8-1.git",
      Branch("master")
    )

  test("decode") {
    val input = Source.fromResource("create-fork.json").mkString
    assertEquals(parser.decode[RepoOut](input), Right(fork))
  }

  test("parentOrRaise") {
    assertEquals(fork.parentOrRaise[IO].unsafeRunSync(), parent)
  }

  test("repo") {
    assertEquals(fork.repo, Repo("scala-steward", "base.g8-1"))
  }
}
