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

package org.scalasteward.core.buildtool

import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.buildtool.gradle.GradleAlg
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.buildtool.scalacli.ScalaCliAlg

final class BuildToolCandidates[F[_]](implicit
    gradleAlg: GradleAlg[F],
    mavenAlg: MavenAlg[F],
    millAlg: MillAlg[F],
    sbtAlg: SbtAlg[F],
    scalaCliAlg: ScalaCliAlg[F],
    F: Monad[F]
) {
  val all: List[BuildToolAlg[F]] = List(gradleAlg, mavenAlg, millAlg, sbtAlg, scalaCliAlg)

  val fallback: List[BuildToolAlg[F]] = List(sbtAlg)

  def findBuildTools(buildRoot: BuildRoot): F[(BuildRoot, List[BuildToolAlg[F]])] =
    all.filterA(_.containsBuild(buildRoot)).map {
      case Nil  => buildRoot -> fallback
      case list => buildRoot -> list
    }
}

object BuildToolCandidates {
  def create[F[_]](implicit
      gradleAlg: GradleAlg[F],
      mavenAlg: MavenAlg[F],
      millAlg: MillAlg[F],
      sbtAlg: SbtAlg[F],
      scalaCliAlg: ScalaCliAlg[F],
      F: Monad[F]
  ): BuildToolCandidates[F] = new BuildToolCandidates
}
