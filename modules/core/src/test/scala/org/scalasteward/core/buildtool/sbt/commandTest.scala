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

package org.scalasteward.core.buildtool.sbt

import munit.ScalaCheckSuite
import org.scalacheck.{Arbitrary, Gen, Prop}

class commandTest extends ScalaCheckSuite {
  private val genWsChar = Gen.oneOf(' ', '\f', '\n', '\r', '\t')
  private val genWsStr = Gen.stringOf(genWsChar)
  private val genNonBlankChar = Arbitrary.arbitrary[Char].retryUntil(!_.isWhitespace)
  private val genNoBlanksStr = Gen.nonEmptyStringOf(genNonBlankChar)

  property("stewardDependencies/crossStewardDependencies with blank subproject") {
    Prop.forAllNoShrink(genWsStr) { wsStr =>
      assertEquals(command.stewardDependencies(wsStr), "stewardDependencies")
      assertEquals(command.crossStewardDependencies(wsStr), "+ stewardDependencies")
    }
  }
  property("stewardDependencies/crossStewardDependencies with non-blank subproject") {
    val gen = for {
      subProj <-
        Gen.zipWith(
          // This will insert whitespace in the middle of `subProj`, which is currently allowed
          // but incorrect. It should be removed once `subProj` validation is added.
          Gen.nonEmptyListOf(Gen.zipWith(genNoBlanksStr, genWsStr)(_ + _)),
          genNoBlanksStr
        )(_.mkString + _)
      leadWss <- genWsStr
      trailWss <- genWsStr
    } yield (leadWss, subProj, trailWss)

    Prop.forAllNoShrink(gen) { case (leadWss, subProj, trailWss) =>
      val input = leadWss + subProj + trailWss
      assertEquals(command.stewardDependencies(input), s"$subProj/stewardDependencies")
      assertEquals(command.crossStewardDependencies(input), s"+ $subProj/stewardDependencies")
    }
  }
}
