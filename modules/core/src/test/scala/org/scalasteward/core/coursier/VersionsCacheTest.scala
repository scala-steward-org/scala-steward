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

package org.scalasteward.core.coursier

import io.circe.parser
import munit.FunSuite
import org.scalasteward.core.coursier.VersionsCache.{Value, VersionWithFirstSeen}
import org.scalasteward.core.data.Version
import org.scalasteward.core.util.Timestamp

import scala.io.Source

class VersionsCacheTest extends FunSuite {
  test("version cache deserialisation without first seen") {
    val input = Source.fromResource("versions-cache-value-without-first-seen.json").mkString
    val expected = Value(
      Timestamp(1000),
      List(
        VersionWithFirstSeen(Version("1.0.0"), None),
        VersionWithFirstSeen(Version("1.0.1"), None)
      ),
      None
    )
    assertEquals(parser.decode[Value](input), Right(expected))
  }

  test("version cache deserialisation with first seen") {
    val input = Source.fromResource("versions-cache-value-with-first-seen.json").mkString
    val expected = Value(
      Timestamp(10002),
      List(
        VersionWithFirstSeen(Version("1.0.0"), Some(Timestamp(10000))),
        VersionWithFirstSeen(Version("1.0.1"), Some(Timestamp(10001)))
      ),
      None
    )
    assertEquals(parser.decode[Value](input), Right(expected))
  }
}
