package org.scalasteward.core.application

import cats.data.StateT
import org.scalasteward.core.mock.MockContext.prepareEnvAlg
import org.scalasteward.core.mock.MockState
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PrepareEnvAlgTest extends AnyFunSuite with Matchers {

  test("addGlobalPlugins") {
    val resultState = prepareEnvAlg
      .addGlobalPlugins(StateT.modify(_.exec(List("fa", "fa")))).runS(MockState.empty).unsafeRunSync()

    resultState.commands shouldBe Vector(
      List("read", "classpath:org/scalasteward/plugin/StewardPlugin.scala"),
      List("create", "/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala"),
      List("create", "/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala"),
      List("fa", "fa"),
      List("rm", "/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala"),
      List("rm", "/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala")
    )

    resultState.logs shouldBe Vector((None, "Add global sbt plugins"))
    resultState.files shouldBe Map.empty
  }

}
