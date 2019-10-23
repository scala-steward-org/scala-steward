package org.scalasteward.core.ammonite

import org.scalasteward.core.ammonite
import org.scalasteward.core.data.{Dependency, GroupId}
import org.scalasteward.core.sbt.defaultScalaBinaryVersion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AmmoniteAlgTest extends AnyFunSuite with Matchers {

  test("parseAmmoniteScript: finds updates") {
    val scriptFile =
      """
        |import $ivy.`com.lihaoyi::scalatags:0.6.0` , $ivy.`org.typelevel::cats-core:1.2.0`  , import cats.implicits._
        |import $plugin.$ivy.`org.scalamacros:::paradise:2.1.0`
        |import $ivy.`com.google.guava:guava:18.0`
        | println("we like cats)"
        |""".stripMargin

    ammonite.parseAmmoniteScript(scriptFile) shouldBe List(
      Dependency(
        GroupId("com.lihaoyi"),
        "scalatags",
        s"scalatags_$defaultScalaBinaryVersion",
        "0.6.0"
      ),
      Dependency(
        GroupId("org.typelevel"),
        "cats-core",
        s"cats-core_$defaultScalaBinaryVersion",
        "1.2.0"
      ),
      Dependency(
        GroupId("org.scalamacros"),
        "paradise",
        s"paradise_$defaultScalaBinaryVersion",
        "2.1.0"
      ),
      Dependency(
        GroupId("com.google.guava"),
        "guava",
        s"guava_$defaultScalaBinaryVersion",
        "18.0"
      )
    )
  }
}
