package org.scalasteward.core.coursier

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalatest.{FunSuite, Matchers}

class CoursierAlgTest extends FunSuite with Matchers {

  test("getArtifactUrl") {
    val dep = Dependency("org.typelevel", "cats-effect", "cats-effect_2.12", "1.0.0")
    coursierAlg
      .getArtifactUrl(dep)
      .runS(MockState.empty)
      .unsafeRunSync() shouldBe MockState.empty.copy(
      commands = Vector(),
      logs = Vector(),
      files = Map()
    )
  }

}
