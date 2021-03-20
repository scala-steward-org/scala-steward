package org.scalasteward.core.nurture

import cats.data.StateT
import cats.effect.IO
import eu.timepit.refined.types.numeric.NonNegInt
import munit.ScalaCheckSuite
import org.scalacheck.Prop._
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.data.ProcessResult.{Ignored, Updated}
import org.scalasteward.core.data.{ProcessResult, Update}

class NurtureAlgTest extends ScalaCheckSuite {
  test("processUpdates with No Limiting") {
    forAll { updates: List[Update] =>
      val obtained = NurtureAlg
        .processUpdates(
          updates,
          _ => StateT[IO, Int, ProcessResult](actionAcc => IO.pure(actionAcc + 1 -> Ignored)),
          None
        )
        .runS(0)
        .unsafeRunSync()
      assertEquals(obtained, updates.size)
    }
  }

  test("processUpdates with Limiting should process all updates up to the limit") {
    forAll { updates: Set[Update] =>
      val (ignorableUpdates, appliableUpdates) = updates.toList.splitAt(updates.size / 2)
      val f: Update => StateT[IO, Int, ProcessResult] = update =>
        StateT[IO, Int, ProcessResult](actionAcc =>
          IO.pure(actionAcc + 1 -> (if (ignorableUpdates.contains(update)) Ignored else Updated))
        )
      val obtained = NurtureAlg
        .processUpdates(
          ignorableUpdates ++ appliableUpdates,
          f,
          NonNegInt.unapply(appliableUpdates.size)
        )
        .runS(0)
        .unsafeRunSync()
      assertEquals(obtained, updates.size)
    }
  }
}
