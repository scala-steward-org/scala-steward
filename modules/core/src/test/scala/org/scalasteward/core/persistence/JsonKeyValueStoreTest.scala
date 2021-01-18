package org.scalasteward.core.persistence

import cats.syntax.all._
import munit.FunSuite
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.mock.MockContext.context._
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
    assertEquals(value, (Some("v1"), None))

    val k1File = config.workspace / "store" / "test" / "v0" / "k1" / "test.json"
    val k2File = config.workspace / "store" / "test" / "v0" / "k2" / "test.json"
    val k3File = config.workspace / "store" / "test" / "v0" / "k3" / "test.json"
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

  test("modifyF, get, set") {
    val kvStore = new JsonKeyValueStore[MockEff, String, String]("test", "0")
    val p = for {
      _ <- kvStore.modifyF("k1")(_ => Option("v0").pure[MockEff])
      v1 <- kvStore.get("k1")
      _ <- kvStore.set("k1", None)
    } yield v1
    val (state, value) = p.run(MockState.empty).unsafeRunSync()
    assertEquals(value, Some("v0"))

    val k1File = config.workspace / "store" / "test" / "v0" / "k1" / "test.json"
    val expected = MockState.empty.copy(
      commands = Vector(
        List("read", k1File.toString),
        List("write", k1File.toString),
        List("read", k1File.toString),
        List("rm", "-rf", k1File.toString)
      )
    )
    assertEquals(state, expected)
  }

  test("cached") {
    val p = for {
      kvStore <- CachingKeyValueStore.wrap(
        new JsonKeyValueStore[MockEff, String, String]("test", "0")
      )
      _ <- kvStore.put("k1", "v1")
      v1 <- kvStore.get("k1")
      v2 <- kvStore.get("k2")
    } yield (v1, v2)
    val (state, value) = p.run(MockState.empty).unsafeRunSync()
    assertEquals(value, (Some("v1"), None))

    val k1File = config.workspace / "store" / "test" / "v0" / "k1" / "test.json"
    val k2File = config.workspace / "store" / "test" / "v0" / "k2" / "test.json"
    val expected = MockState.empty.copy(
      commands = Vector(List("write", k1File.toString), List("read", k2File.toString)),
      files = Map(k1File -> """"v1"""")
    )
    assertEquals(state, expected)
  }
}
