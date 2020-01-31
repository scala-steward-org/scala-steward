package org.scalasteward.core.persistence

import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonKeyValueStoreTest extends AnyFunSuite with Matchers {
  test("put, get") {
    val kvStore = new JsonKeyValueStore[MockEff, String, String]("test", "0")
    val p = for {
      _ <- kvStore.put("k1", "v1")
      v1 <- kvStore.get("k1")
      _ <- kvStore.put("k2", "v2")
      v3 <- kvStore.get("k3")
    } yield (v1, v3)
    val (state, value) = p.run(MockState.empty).unsafeRunSync()

    val k1File = config.workspace / "store" / "test" / "v0" / "k1" / "test.json"
    val k2File = config.workspace / "store" / "test" / "v0" / "k2" / "test.json"
    val k3File = config.workspace / "store" / "test" / "v0" / "k3" / "test.json"
    value shouldBe (Some("v1") -> None)
    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", k1File.toString),
        List("read", k1File.toString),
        List("write", k2File.toString),
        List("read", k3File.toString)
      ),
      files = Map(
        k1File -> """"v1"""",
        k2File -> """"v2""""
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
