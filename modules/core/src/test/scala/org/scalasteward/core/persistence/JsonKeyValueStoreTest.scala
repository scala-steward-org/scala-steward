package org.scalasteward.core.persistence

import munit.FunSuite
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockEff, MockState}

class JsonKeyValueStoreTest extends FunSuite {
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
    assertEquals(value, (Some("v1") -> None))
    val expected = MockState.empty.copy(
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
    assertEquals(state, expected)
  }

  test("modify") {
    val kvStore = new JsonKeyValueStore[MockEff, String, String]("test", "0")
    val p = for {
      _ <- kvStore.modify("k1")(_ => Some("v0"))
      v1 <- kvStore.get("k1")
    } yield v1
    assertEquals(p.runA(MockState.empty).unsafeRunSync(), Some("v0"))
  }
}
