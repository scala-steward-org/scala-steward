/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.sbt.plugin

import sbt.Keys._
import sbt._
import scala.util.Try

object StewardPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val stewardDependencies =
      taskKey[Unit]("Prints dependencies and resolvers as JSON for consumption by Scala Steward.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      stewardDependencies := {
        val log = streams.value.log
        val scalaBinaryVersionValue = scalaBinaryVersion.value
        val scalaVersionValue = scalaVersion.value
        val sbtCredentials = findCredentials.value

        val libraryDeps = libraryDependencies.value
          .map(moduleId => toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue))

        val scalafixDeps = findScalafixDependencies.value
          .getOrElse(Seq.empty)
          .map(moduleId =>
            toDependency(
              moduleId,
              scalaVersionValue,
              scalaBinaryVersionValue,
              Some("scalafix-rule")
            )
          )
        val dependencies = libraryDeps ++ scalafixDeps

        def getCredentials(url: URL): Option[Resolver.Credentials] =
          Try(Credentials.forHost(sbtCredentials, url.getHost)).toOption.flatten
            .map(c => Resolver.Credentials(c.userName, c.passwd))

        val resolvers = fullResolvers.value.collect {
          case repo: MavenRepository if !repo.root.startsWith("file:") =>
            val creds = getCredentials(new URL(repo.root))
            Resolver.MavenRepository(repo.name, repo.root, creds)
          case repo: URLRepository =>
            val ivyPatterns = repo.patterns.ivyPatterns.mkString
            val creds = getCredentials(new URL(ivyPatterns))
            Resolver.IvyRepository(repo.name, ivyPatterns, creds)
        }

        val sb = new StringBuilder()
        val ls = System.lineSeparator()
        sb.append("--- snip ---").append(ls)
        dependencies.foreach(d => sb.append(d.asJson).append(ls))
        resolvers.foreach(r => sb.append(r.asJson).append(ls))
        log.info(sb.result())
      }
    )

  // base on mdoc
  // https://github.com/scalameta/mdoc/blob/62cacfbc4c7b618228e349d4f460061916398424/mdoc-sbt/src/main/scala/mdoc/MdocPlugin.scala#L164-L182
  lazy val findScalafixDependencies: Def.Initialize[Option[Seq[ModuleID]]] = Def.settingDyn {
    try {
      val scalafixDependencies = SettingKey[Seq[ModuleID]]("scalafixDependencies").?
      Def.setting {
        scalafixDependencies.value
      }
    } catch {
      case _: ClassNotFoundException => Def.setting(None)
    }
  }

  lazy val findCredentials: Def.Initialize[Task[Seq[Credentials]]] = Def.taskDyn {
    try {
      val allCredentials = TaskKey[Seq[Credentials]]("allCredentials").?
      Def.task {
        allCredentials.value.getOrElse(Nil)
      }
    } catch {
      case _: ClassNotFoundException => Def.task(credentials.value)
    }
  }

  private def crossName(
      moduleId: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String
  ): Option[String] =
    CrossVersion(moduleId.crossVersion, scalaVersion, scalaBinaryVersion).map(_(moduleId.name))

  private def toDependency(
      moduleId: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String,
      configurations: Option[String] = None
  ): Dependency =
    Dependency(
      groupId = moduleId.organization,
      artifactId = ArtifactId(moduleId.name, crossName(moduleId, scalaVersion, scalaBinaryVersion)),
      version = moduleId.revision,
      sbtVersion = moduleId.extraAttributes.get("e:sbtVersion"),
      scalaVersion = moduleId.extraAttributes.get("e:scalaVersion"),
      configurations = configurations.orElse(moduleId.configurations)
    )

  final private case class ArtifactId(
      name: String,
      maybeCrossName: Option[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "name" -> strToJson(name),
          "maybeCrossName" -> optToJson(maybeCrossName.map(strToJson))
        )
      )
  }

  final private case class Dependency(
      groupId: String,
      artifactId: ArtifactId,
      version: String,
      sbtVersion: Option[String],
      scalaVersion: Option[String],
      configurations: Option[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "groupId" -> strToJson(groupId),
          "artifactId" -> artifactId.asJson,
          "version" -> strToJson(version),
          "sbtVersion" -> optToJson(sbtVersion.map(strToJson)),
          "scalaVersion" -> optToJson(scalaVersion.map(strToJson)),
          "configurations" -> optToJson(configurations.map(strToJson))
        )
      )
  }

  sealed trait Resolver extends Product with Serializable {
    def asJson: String
  }

  object Resolver {
    final case class Credentials(user: String, pass: String) {
      def asJson: String =
        objToJson(
          List("user" -> strToJson(user), "pass" -> strToJson(pass))
        )
    }

    final case class MavenRepository(
        name: String,
        location: String,
        credentials: Option[Credentials]
    ) extends Resolver {
      override def asJson: String =
        objToJson(
          List(
            "MavenRepository" -> objToJson(
              List("name" -> strToJson(name), "location" -> strToJson(location)) ++
                credentials.map(c => "credentials" -> c.asJson).toList
            )
          )
        )
    }

    final case class IvyRepository(name: String, pattern: String, credentials: Option[Credentials])
        extends Resolver {
      override def asJson: String =
        objToJson(
          List(
            "IvyRepository" -> objToJson(
              List("name" -> strToJson(name), "pattern" -> strToJson(pattern)) ++
                credentials.map(c => "credentials" -> c.asJson).toList
            )
          )
        )
    }
  }

  private def strToJson(str: String): String =
    s""""$str""""

  private def optToJson(opt: Option[String]): String =
    opt.getOrElse("null")

  private def objToJson(obj: List[(String, String)]): String =
    obj.map { case (k, v) => s""""$k": $v""" }.mkString("{ ", ", ", " }")
}
