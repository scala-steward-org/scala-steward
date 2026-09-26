/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.persistence

import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import munit.FunSuite
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockState}

class JsonKeyValueStoreTest extends FunSuite {
  test("put, get") {
    val p = for {
      kvStore <- JsonKeyValueStore.create[MockEff, String, String]("test-1", "0")
      _ <- kvStore.put("k1", "v1")
      v1 <- kvStore.get("k1")
      _ <- kvStore.put("k2", "v2")
      v3 <- kvStore.get("k3")
    } yield (v1, v3)
    val (state, value) = p.runSA(MockState.empty).unsafeRunSync()
    assertEquals(value, (Some("v1"), None))

    val k1File = config.workspace / "store" / "test-1" / "v0" / "k1" / "test-1.json"
    val k2File = config.workspace / "store" / "test-1" / "v0" / "k2" / "test-1.json"
    val k3File = config.workspace / "store" / "test-1" / "v0" / "k3" / "test-1.json"
    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("write", k1File.toString),
        Cmd("read", k1File.toString),
        Cmd("write", k2File.toString),
        Cmd("read", k3File.toString)
      ),
      files = Map(
        k1File -> """"v1"""",
        k2File -> """"v2""""
      )
    )
    assertEquals(state, expected)
  }

  test("modifyF, get, set") {
    val p = for {
      kvStore <- JsonKeyValueStore.create[MockEff, String, String]("test-2", "0")
      _ <- kvStore.modifyF("k1")(_ => Option("v0").pure[MockEff])
      v1 <- kvStore.get("k1")
      _ <- kvStore.set("k1", None)
    } yield v1
    val (state, value) = p.runSA(MockState.empty).unsafeRunSync()
    assertEquals(value, Some("v0"))

    val k1File = config.workspace / "store" / "test-2" / "v0" / "k1" / "test-2.json"
    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("read", k1File.toString),
        Cmd("write", k1File.toString),
        Cmd("read", k1File.toString),
        Cmd("rm", "-rf", k1File.toString)
      )
    )
    assertEquals(state, expected)
  }

  test("cached") {
    val p = for {
      kvStore <- JsonKeyValueStore
        .create[MockEff, String, String]("test-3", "0")
        .flatMap(CachingKeyValueStore.wrap(_))
      _ <- kvStore.put("k1", "v1")
      v1 <- kvStore.get("k1")
      v2 <- kvStore.get("k2")
    } yield (v1, v2)
    val (state, value) = p.runSA(MockState.empty).unsafeRunSync()
    assertEquals(value, (Some("v1"), None))

    val k1File = config.workspace / "store" / "test-3" / "v0" / "k1" / "test-3.json"
    val k2File = config.workspace / "store" / "test-3" / "v0" / "k2" / "test-3.json"
    val expected = MockState.empty.copy(
      trace = Vector(Cmd("write", k1File.toString), Cmd("read", k2File.toString)),
      files = Map(k1File -> """"v1"""")
    )
    assertEquals(state, expected)
  }

  test("get with malformed JSON") {
    val p = for {
      kvStore <- JsonKeyValueStore.create[MockEff, String, String]("test-4", "0")
      v1 <- kvStore.get("k1")
    } yield v1

    val k1File = config.workspace / "store" / "test-4" / "v0" / "k1" / "test-4.json"
    val k1Mapping = k1File -> """ "v1 """
    val value = MockState.empty.addFiles(k1Mapping).flatMap(p.runA).unsafeRunSync()
    assertEquals(value, None)
  }
}
