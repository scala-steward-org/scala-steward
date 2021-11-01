package org.scalasteward.core.repocache

import munit.CatsEffectSuite
import cats.syntax.all._
import org.scalasteward.core.mock.MockContext.context.refreshErrorAlg
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.vcs.data.Repo

class RefreshErrorAlgTest extends CatsEffectSuite {
  test("skipIfFailedRecently: not failed") {
    val repo = Repo("refresh-error", "test-1")
    val expected = 42
    refreshErrorAlg.skipIfFailedRecently(repo)(MockEff.pure(expected)).runA(MockState.empty).map {
      obtained => assertEquals(obtained, expected)
    }
  }

  test("skipIfFailedRecently: failed") {
    val repo = Repo("refresh-error", "test-2")
    val error = new Throwable("zonk")
    val p = refreshErrorAlg.persistError(repo)(MockEff.raiseError(error)) >>
      refreshErrorAlg.skipIfFailedRecently(repo)(MockEff.pure(42))

    p.runA(MockState.empty).attempt.map { obtained =>
      assertEquals(obtained.fold(_.getMessage, _.toString), error.getMessage)
    }
  }
}
