package org.scalasteward.core.vcs.data

import org.scalasteward.core.vcs.data.Repo.repoKeyDecoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalasteward.core.application.SupportedVCS._

class RepoTest extends AnyFunSuite with Matchers {
  test("decode") {
    repoKeyDecoder("owner/repo") shouldBe Some(Repo(GitHub, "owner", "repo"))
  }

  test("decode sub group") {
    repoKeyDecoder("group1/group2/project1") shouldBe Some(
      Repo(GitHub, "group1/group2", "project1")
    )
  }

  test("decode explicit gitHost repo") {
    val hosts = List(GitHub, Gitlab, Bitbucket, BitbucketServer)
    hosts.map(host => repoKeyDecoder(s"${host.asString}:owner/repo")) shouldBe hosts.map(host =>
      Some(Repo(host, "owner", "repo"))
    )
  }
}
