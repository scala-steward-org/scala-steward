package org.scalasteward.core.persistence

import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonKeyValueStoreTest extends AnyFunSuite with Matchers {
  test("put, get, getMany, delete") {
    val kvStore = new JsonKeyValueStore[MockEff, String, String]("test", "0")
    val p = for {
      _ <- kvStore.put("k1", "v1")
      v1 <- kvStore.get("k1")
      _ <- kvStore.put("k2", "v2")
      v3 <- kvStore.get("k3")
    } yield (v1, v3)
    val (state, value) = p.run(MockState.empty).unsafeRunSync()

    val file = config.workspace / "test_v0.json"
    value shouldBe (Some("v1") -> None)
    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", file.toString),
        List("write", file.toString),
        List("read", file.toString),
        List("read", file.toString),
        List("write", file.toString),
        List("read", file.toString)
      ),
      files = Map(
        file -> """|{
                   |  "k1" : "v1",
                   |  "k2" : "v2"
                   |}""".stripMargin.trim
      )
    )
  }

  test("update") {
    val kvStore = new JsonKeyValueStore[MockEff, String, String]("test", "0")
    val p = for {
      _ <- kvStore.update("k1")(_.fold("v0")(_ + "v1"))
      v1 <- kvStore.get("k1")
    } yield v1
    p.runA(MockState.empty).unsafeRunSync() shouldBe Some("v0")
  }
}
