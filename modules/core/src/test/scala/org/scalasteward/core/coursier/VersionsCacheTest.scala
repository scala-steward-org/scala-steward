package org.scalasteward.core.coursier

import io.circe.parser
import munit.FunSuite
import org.scalasteward.core.coursier.VersionsCache.Value
import org.scalasteward.core.data.Version
import org.scalasteward.core.util.Timestamp

import scala.io.Source

class VersionsCacheTest extends FunSuite {
  test("version cache deserialisation without first seen") {
    val input = Source.fromResource("versions-cache-value-without-first-seen.json").mkString
    val expected = Value(Timestamp(1000), List(Version("1.0.0"), Version("1.0.1")), None)
    assertEquals(parser.decode[Value](input), Right(expected))
  }
}
