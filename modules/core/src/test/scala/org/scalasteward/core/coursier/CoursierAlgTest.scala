package org.scalasteward.core.coursier

import org.scalasteward.core.data.{Dependency, GroupId}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CoursierAlgTest extends AnyFunSuite with Matchers {

  test("getArtifactUrl") {
    val dep = Dependency(GroupId("org.typelevel"), "cats-effect", "cats-effect_2.12", "1.0.0")
    val (state, result) = coursierAlg
      .getArtifactUrl(dep)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty.copy(
      commands = Vector(),
      logs = Vector(),
      files = Map()
    )
    result shouldBe Some("https://typelevel.org/cats-effect/")
  }

  test("getArtifactIdUrlMapping") {
    val dependencies = List(
      Dependency(GroupId("org.typelevel"), "cats-core", "cats-core_2.12", "1.6.0"),
      Dependency(GroupId("org.typelevel"), "cats-effect", "cats-effect_2.12", "1.0.0")
    )
    val (state, result) = coursierAlg
      .getArtifactIdUrlMapping(dependencies)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty.copy(
      commands = Vector(),
      logs = Vector(),
      files = Map()
    )
    result shouldBe Map(
      "cats-core" -> "https://github.com/typelevel/cats",
      "cats-effect" -> "https://typelevel.org/cats-effect/"
    )
  }

}
