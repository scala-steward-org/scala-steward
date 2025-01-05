package org.scalasteward.core.application

import munit.CatsEffectSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.context.reposFilesLoader
import org.scalasteward.core.mock.{MockConfig, MockEffOps, MockState}
import org.scalasteward.core.util.Nel

class ReposFilesLoaderTest extends CatsEffectSuite {
  test("non-empty repos file") {
    val initialState = MockState.empty.addUris(MockConfig.reposFile -> "- a/b\n- c/d:e")
    val obtained =
      reposFilesLoader.loadAll(Nel.one(MockConfig.reposFile)).compile.toList.runA(initialState)
    assertIO(obtained, List(Repo("a", "b"), Repo("c", "d", Some(Branch("e")))))
  }

  test("malformed repos file") {
    val initialState = MockState.empty.addUris(MockConfig.reposFile -> " - a/b")
    val obtained =
      reposFilesLoader.loadAll(Nel.one(MockConfig.reposFile)).compile.toList.runA(initialState)
    assertIO(obtained, List.empty)
  }

  test("non-existing repos file") {
    val initialState = MockState.empty
    val obtained =
      reposFilesLoader.loadAll(Nel.one(MockConfig.reposFile)).compile.toList.runA(initialState)
    assertIO(obtained.attempt.map(_.isLeft), true)
  }
}
