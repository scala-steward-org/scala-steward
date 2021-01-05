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

package org.scalasteward.mill.plugin

import coursier.core.{Authentication, Repository}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import mill._
import mill.define.{Discover, ExternalModule}
import mill.eval.Evaluator
import mill.scalalib._
import ujson._

object StewardPlugin extends ExternalModule {

  implicit def millScoptEvaluatorReads[E]: mill.main.EvaluatorScopt[E] =
    new mill.main.EvaluatorScopt[E]()

  def extractDeps(ev: Evaluator) =
    T.command {
      val modules = T.traverse(findModules(ev))(toModuleDep)
      T.task {
        val m = modules()
        Obj(
          "modules" -> Arr.from(m.map(_.toJson))
        )
      }
    }

  def findModules(ev: Evaluator) =
    ev.rootModule.millInternal.modules.collect { case j: JavaModule => j }

  def toModuleDep(m: JavaModule) = {
    val artifactMods = m match {
      case scalaMod: ScalaModule =>
        T.task(
          Some(
            (scalaMod.artifactScalaVersion(), scalaMod.scalaVersion(), scalaMod.platformSuffix())
          )
        )
      case _ => T.task(None)
    }
    val dependencies = T.task {
      val ivy = m.ivyDeps()
      val mod = artifactMods()
      ivy.iterator.toSeq.map(Dependency.fromDep(_, mod))
    }

    T.task {
      val resolvers = m.repositories.map(Repo).filterNot(_.isLocal)
      val deps = dependencies()
      ModuleDependencies(m.millModuleSegments.render, resolvers, deps)
    }
  }

  lazy val millDiscover: Discover[StewardPlugin.this.type] = Discover[this.type]

  case class ArtifactId(
      name: String,
      maybeCrossName: Option[String]
  ) {
    def toJson =
      Obj(
        "name" -> Str(name),
        "maybeCrossName" -> maybeCrossName.map(Str).getOrElse(Null)
      )
  }

  case class Dependency(
      groupId: String,
      artifactId: ArtifactId,
      version: String
  ) {
    def toJson =
      Obj(
        "groupId" -> Str(groupId),
        "artifactId" -> artifactId.toJson,
        "version" -> Str(version)
      )
  }

  object Dependency {
    def fromDep(dep: Dep, modifiers: Option[(String, String, String)]) = {
      val artifactId = ArtifactId(
        dep.dep.module.name.value,
        modifiers.map { case (binary, full, platform) =>
          dep.artifactName(binary, full, platform)
        }
      )
      Dependency(dep.dep.module.organization.value, artifactId, dep.dep.version)
    }
  }

  case class Repo(repository: Repository) {
    def isLocal =
      repository match {
        case repository: IvyRepository   => repository.pattern.string.startsWith("file")
        case repository: MavenRepository => repository.root.startsWith("file")
        case _                           => true
      }

    val authJson = (a: Authentication) =>
      Obj(
        "user" -> Str(a.user),
        "pass" -> a.passwordOpt.map(Str).getOrElse(Null),
        "realm" -> a.realmOpt.map(Str).getOrElse(Null)
      )

    def toJson =
      repository match {
        case m: MavenRepository =>
          Obj(
            "url" -> Str(m.root),
            "type" -> Str("maven"),
            "auth" -> m.authentication
              .map(authJson)
              .getOrElse(Null)
          )
        case ivy: IvyRepository =>
          Obj(
            "pattern" -> Str(ivy.pattern.string),
            "type" -> Str("ivy"),
            "auth" -> ivy.authentication
              .map(authJson)
              .getOrElse(Null)
          )
        case _ => Null
      }
  }

  case class ModuleDependencies(
      name: String,
      resolvers: Seq[Repo],
      dependencies: Seq[Dependency]
  ) {
    def toJson =
      Obj(
        "name" -> Str(name),
        "repositories" -> Arr.from(resolvers.map(_.toJson).filterNot(_.isNull)),
        "dependencies" -> Arr.from(dependencies.map(_.toJson))
      )
  }

}
