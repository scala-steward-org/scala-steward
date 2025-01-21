package org.scalasteward.core.repocache

import cats.syntax.all.*
import munit.CatsEffectSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.mock.MockContext.context.refreshErrorAlg
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockState}

class RefreshErrorAlgTest extends CatsEffectSuite {
  test("throwIfFailedRecently: not failed") {
    val repo = Repo("refresh-error", "test-1")
    val p = refreshErrorAlg.throwIfFailedRecently(repo)

    val expected = "()"
    p.runA(MockState.empty).attempt.map { obtained =>
      val obtainedStr = obtained.fold(_.getMessage, _.toString).take(expected.length)
      assertEquals(obtainedStr, expected)
    }
  }

  test("throwIfFailedRecently: failed") {
    val repo = Repo("refresh-error", "test-2")
    val p = refreshErrorAlg.persistError(repo)(MockEff.raiseError(new Throwable())).attempt >>
      refreshErrorAlg.throwIfFailedRecently(repo)

    val expected = "Skipping due to previous error"
    p.runA(MockState.empty).attempt.map { obtained =>
      val obtainedStr = obtained.fold(_.getMessage, _.toString).take(expected.length)
      assertEquals(obtainedStr, expected)
    }
  }
}
