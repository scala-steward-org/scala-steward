package org.scalasteward.core.repocache

import cats.syntax.all._
import munit.CatsEffectSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.mock.MockContext.context.refreshErrorAlg
import org.scalasteward.core.mock.{MockEff, MockState}

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
    val p = refreshErrorAlg.persistError(repo)(MockEff.raiseError(new Throwable())).attempt >>
      refreshErrorAlg.skipIfFailedRecently(repo)(MockEff.unit)

    val expected = "Skipping due to previous error"
    p.runA(MockState.empty).attempt.map { obtained =>
      val message = obtained.fold(_.getMessage, _.toString).take(expected.length)
      assertEquals(message, expected)
    }
  }
}
