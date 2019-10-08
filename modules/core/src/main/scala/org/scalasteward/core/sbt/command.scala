/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.sbt

object command {
  val dependencyUpdates = "dependencyUpdates"
  val libraryDependenciesAsJson = "show libraryDependenciesAsJson"
  val reloadPlugins = "reload plugins"
  val scalafix = "scalafix"
  val testScalafix = "test:scalafix"
  val scalafixEnable = "scalafixEnable"
  val setDependencyUpdatesFailBuild = "set every dependencyUpdatesFailBuild := false"
}
