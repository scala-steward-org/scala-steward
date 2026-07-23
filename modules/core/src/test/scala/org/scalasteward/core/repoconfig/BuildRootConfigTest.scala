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

package org.scalasteward.core.repoconfig

import cats.syntax.all.*
import io.circe.testing.CodecTests
import io.circe.testing.instances.*
import io.circe.{DecodingFailure, Json}
import munit.{DisciplineSuite, FunSuite}
import org.scalacheck.{Arbitrary, Gen, Prop}

class BuildRootConfigTest extends FunSuite with DisciplineSuite {
  private val genRelPathStr: Gen[String] =
    Gen.nonEmptyStringOf(Arbitrary.arbitrary[Char]).filterNot(_.contains(':'))
  private val genSubProjStr: Gen[String] =
    Gen.nonEmptyStringOf(Arbitrary.arbitrary[Char])

  private val genBuildRootConfig: Gen[BuildRootConfig] =
    Gen.zipWith(
      genRelPathStr,
      Gen.frequency(1 -> Gen.const(""), 4 -> genSubProjStr)
    )(BuildRootConfig.apply)

  implicit private val arbBuildRootConfig: Arbitrary[BuildRootConfig] = Arbitrary(
    genBuildRootConfig
  )

  test("Decode empty string") {
    val expected = Right(BuildRootConfig("", ""))
    val obtained = Json.fromString("").as[BuildRootConfig]
    assertEquals(obtained, expected)
  }
  property("Decode `relativePath` only") {
    Prop.forAllNoShrink(genRelPathStr) { (relPathStr: String) =>
      val expected = Right(BuildRootConfig(relPathStr, ""))
      val obtained = Json.fromString(relPathStr).as[BuildRootConfig]
      assertEquals(obtained, expected)
    }
  }
  property("Decode `relativePath` with separator and empty `subProject` part") {
    Prop.forAllNoShrink(genRelPathStr) { (relPathStr: String) =>
      val input = s"$relPathStr:"
      val expected =
        DecodingFailure(s"$input\nThe subproject part cannot be empty after ':'", Nil)
          .asLeft[BuildRootConfig]
      val obtained = Json.fromString(input).as[BuildRootConfig]
      assertEquals(obtained, expected)
    }
  }
  property("Decode `relativePath` with separator and non-empty `subProject` part") {
    Prop.forAllNoShrink(genRelPathStr, genSubProjStr) { (relPathStr: String, subProjStr) =>
      val input = s"$relPathStr:$subProjStr"
      val expected = Right(BuildRootConfig(relPathStr, subProjStr))
      val obtained = Json.fromString(input).as[BuildRootConfig]
      assertEquals(obtained, expected)
    }
  }

  checkAll("CodecTests", CodecTests[BuildRootConfig].codec)
}
