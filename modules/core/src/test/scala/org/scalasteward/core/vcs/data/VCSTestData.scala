package org.scalasteward.core.vcs.data

import org.scalasteward.core.git._
import org.http4s._
import org.scalasteward.core.git.Sha1._

object VCSTestData extends VCSTestData

trait VCSTestData {

  val repo = Repo("fthomas", "base.g8")

  val parent = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/base.g8.git",
    Branch("master")
  )

  val fork = RepoOut(
    "base.g8-1",
    UserOut("scala-steward"),
    Some(parent),
    uri"https://github.com/scala-steward/base.g8-1.git",
    Branch("master")
  )

  val defaultBranch = BranchOut(
    Branch("master"),
    CommitOut(Sha1(HexString("07eb2a203e297c8340273950e98b2cab68b560c1")))
  )
}
