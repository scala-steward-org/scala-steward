/*
 * Copyright 2018-2023 Scala Steward contributors
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
      (scalaLangGroupId, ArtifactId("scala3-compiler")),
      (scalaLangGroupId, ArtifactId("scala3-library")),
      (scalaLangGroupId, ArtifactId("scala3-library_sjs1"))
    )

  val scalaLangModules: List[(GroupId, ArtifactId)] =
    scala2LangModules ++ scala3LangModules

  val scalaNextMinVersion: Version = Version("3.4.0-NIGHTLY")
}
