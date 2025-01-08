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

package org.scalasteward.core

package object data {
  val scalaLangGroupId: GroupId = GroupId("org.scala-lang")

  val scala2LangModules: List[(GroupId, ArtifactId)] =
    List(
      (scalaLangGroupId, ArtifactId("scala-compiler")),
      (scalaLangGroupId, ArtifactId("scala-library")),
      (scalaLangGroupId, ArtifactId("scala-reflect")),
      (scalaLangGroupId, ArtifactId("scalap"))
    )

  val scala3LangModules: List[(GroupId, ArtifactId)] =
    List(
      "scala3-compiler",
      "scala3-library",
      "scala3-library_sjs1",
      "scala2-library-cc-tasty-experimental",
      "scala2-library-tasty-experimental",
      "scala3-language-server",
      "scala3-presentation-compiler",
      "scala3-staging",
      "scala3-tasty-inspector",
      "scaladoc",
      "tasty-core"
    ).map(artifactId => (scalaLangGroupId, ArtifactId(artifactId)))

  val scalaLangModules: List[(GroupId, ArtifactId)] =
    scala2LangModules ++ scala3LangModules

  val scalaNextMinVersion: Version = Version("3.4.0-NIGHTLY")
}
