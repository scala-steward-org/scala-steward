package org.scalasteward.core.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.util.Nel

class UpdateAlgTest extends FunSuite {
  test("isUpdateFor") {
    val dependency = ("io.circe".g % ("circe-refined", "circe-refined_2.12").a % "0.11.2").cross
    val update = ("io.circe".g %%
      Nel.of(
        Nel.of(("circe-core", "circe-core_2.12").a),
        Nel.of(
          ("circe-refined", "circe-refined_2.12").a,
          ("circe-refined", "circe-refined_sjs0.6_2.12").a
        )
      ) % "0.11.2" %> "0.12.3").group
    assert(UpdateAlg.isUpdateFor(update, dependency))
  }
}
