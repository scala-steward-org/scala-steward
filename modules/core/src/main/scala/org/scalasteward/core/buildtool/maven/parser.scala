/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.buildtool.maven

import atto.Atto._
import atto._
import cats.implicits._
import org.scalasteward.core.data._

object parser {
  private val colon: Parser[Char] = char(':')

  private val underscore: Parser[Char] = char('_')

  private val stringNoSpace: Parser[String] =
    many1(noneOf(" ")).map(_.mkString_(""))

  private val stringNoSpaceNoColon: Parser[String] =
    many1(noneOf(" :")).map(_.mkString_(""))

  private val stringNoSpaceNoColonNoUnderscore: Parser[String] =
    many1(noneOf(" :_")).map(_.mkString_(""))

  private val artifactId: Parser[ArtifactId] =
    for {
      name <- stringNoSpaceNoColonNoUnderscore
      suffix <- opt(underscore ~ stringNoSpaceNoColon)
    } yield ArtifactId(name, suffix.map { case (c, str) => name + c + str })

  private val configurations: Parser[Option[String]] =
    opt(stringNoSpaceNoColon).map(_.filterNot(_ === "compile"))

  private val dependency: Parser[Dependency] =
    for {
      _ <- opt(string("[INFO]") ~ many(whitespace))
      groupId <- stringNoSpaceNoColon.map(GroupId.apply) <~ colon
      artifactId <- artifactId <~ colon <~ string("jar") <~ colon
      version <- stringNoSpaceNoColon <~ colon
      configurations <- configurations
    } yield Dependency(
      groupId = groupId,
      artifactId = artifactId,
      version = version,
      configurations = configurations
    )

  def parseDependencies(input: List[String]): List[Dependency] =
    input.flatMap(line => dependency.parse(line).done.option)

  private val resolver: Parser[Resolver] =
    for {
      _ <- many(whitespace) ~ string("id:") ~ whitespace
      id <- stringNoSpace
      _ <- many(whitespace) ~ string("url:") ~ whitespace
      url <- stringNoSpace
    } yield Resolver.MavenRepository(id, url, None)

  def parseResolvers(input: List[String]): List[Resolver] =
    input.mkString.split("""\[INFO\]""").toList.flatMap(line => resolver.parse(line).done.option)
}
