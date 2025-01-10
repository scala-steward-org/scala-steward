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

package org.scalasteward.core.buildtool.gradle

import cats.implicits.*
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Version}
import org.tomlj.{Toml, TomlTable}
import scala.jdk.CollectionConverters.*

object gradleParser {
  def parseDependenciesAndPlugins(input: String): (List[Dependency], List[Dependency]) = {
    val parsed = Toml.parse(input)
    val versionsTable = getTableSafe(parsed, "versions")
    val librariesTable = getTableSafe(parsed, "libraries")
    val pluginsTable = getTableSafe(parsed, "plugins")

    val dependencies = collectEntries(librariesTable, parseDependency(_, versionsTable))
    val plugins = collectEntries(pluginsTable, parsePlugin(_, versionsTable))

    (dependencies, plugins)
  }

  private def collectEntries[A: Ordering](table: TomlTable, f: TomlTable => Option[A]): List[A] = {
    val aSet = table.entrySet().asScala.map(_.getValue).flatMap {
      case t: TomlTable => f(t)
      case _            => None
    }
    aSet.toList.sorted
  }

  private def parseDependency(lib: TomlTable, versions: TomlTable): Option[Dependency] =
    for {
      case (groupId, artifactId) <- parseModuleObj(lib).orElse(parseModuleString(lib))
      version <- parseVersion(lib, versions)
    } yield Dependency(groupId, artifactId, version)

  private def parseModuleObj(lib: TomlTable): Option[(GroupId, ArtifactId)] =
    for {
      groupId <- getStringSafe(lib, "group").map(GroupId(_))
      artifactId <- getStringSafe(lib, "name").map(ArtifactId(_))
    } yield (groupId, artifactId)

  private def parseModuleString(lib: TomlTable): Option[(GroupId, ArtifactId)] =
    getStringSafe(lib, "module").flatMap {
      _.split(':') match {
        case Array(g, a) => Some((GroupId(g), ArtifactId(a)))
        case _           => None
      }
    }

  private def parsePlugin(plugin: TomlTable, versions: TomlTable): Option[Dependency] =
    for {
      id <- getStringSafe(plugin, "id")
      groupId = GroupId(id)
      artifactId = ArtifactId(s"$id.gradle.plugin")
      version <- parseVersion(plugin, versions)
    } yield Dependency(groupId, artifactId, version)

  private def parseVersion(table: TomlTable, versions: TomlTable): Option[Version] = {
    def versionString = getStringSafe(table, "version")
    def versionRef = getStringSafe(table, "version.ref").flatMap(getStringSafe(versions, _))
    versionString.orElse(versionRef).map(Version.apply)
  }

  private def getTableSafe(table: TomlTable, key: String): TomlTable =
    Option
      .when(table.contains(key) && table.isTable(key))(table.getTableOrEmpty(key))
      .getOrElse(emptyTable)

  private val emptyTable: TomlTable = Toml.parse("")

  private def getStringSafe(table: TomlTable, key: String): Option[String] =
    Option.when(table.contains(key) && table.isString(key))(table.getString(key))
}
