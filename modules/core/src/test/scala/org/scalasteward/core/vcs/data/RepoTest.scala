package org.scalasteward.core.vcs.data

import org.scalasteward.core.vcs.data.Repo.repoKeyDecoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RepoTest extends AnyFunSuite with Matchers {

  test("decode") {
    repoKeyDecoder("owner/repo") shouldBe Some(Repo("owner", "repo"))
  }

  test("decode sub group") {
    repoKeyDecoder("group1/group2/project1") shouldBe Some(Repo("group1/group2", "project1"))
  }
}
