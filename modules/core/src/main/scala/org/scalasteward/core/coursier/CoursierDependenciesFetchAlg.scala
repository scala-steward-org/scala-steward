/*
 * Copyright 2018-2022 Scala Steward contributors
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

import cats.Parallel
import cats.effect._
import cats.implicits._
import coursier._
import coursier.cache.FileCache

import java.net.URLClassLoader

/**
 * An interface to fetch dependencies for the coursier alg via coursier.
 */
trait CoursierDependenciesFetchAlg[F[_]] {
  def classLoader(dependencies: Seq[String]): F[ClassLoader]
}

object CoursierDependenciesFetchAlg {
  def create[F[_]](implicit parallel: Parallel[F], F: Sync[F]): CoursierDependenciesFetchAlg[F] =
    (dependencies: Seq[String]) =>
      Fetch[F](FileCache[F]())
        .withDependencies(dependencies.map { dep =>
          val Array(org, name, version) = dep.split(':')
          Dependency(Module(Organization(org), ModuleName(name)), version)
        })
        .io
        .map(result => new URLClassLoader(result.map(_.toURI.toURL).toArray))
}
