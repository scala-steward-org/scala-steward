package org.scalasteward.core.util

import cats.implicits._
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite

class JsonKeyValueStoreTest extends AnyFunSuite with Matchers {
  test("put, get, getOrElse") {
    val kvStore = new JsonKeyValueStore[MockEff, String, String]("test", "0")
    val p = kvStore.put("k1", "v1") >> (kvStore.get("k1"), kvStore.getOrElse("k2", "v2")).tupled
    val (state, value) = p.run(MockState.empty).unsafeRunSync()

    val file = config.workspace / "test_v0.json"
    value shouldBe (Some("v1") -> "v2")
    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", file.toString),
        List("write", file.toString),
        List("read", file.toString),
        List("read", file.toString)
      ),
      files = Map(
        file -> """|{
                   |  "k1" : "v1"
                   |}""".stripMargin.trim
      )
    )
  }
}
