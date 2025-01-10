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

package org.scalasteward.core.buildtool.maven

import cats.parse.Rfc5234.wsp
import cats.parse.{Parser, Parser0}
import cats.syntax.all.*
import org.scalasteward.core.data.*

object parser {
  private val colon: Parser[Char] =
    Parser.charIn(':')

  private val underscore: Parser[Char] =
    Parser.charIn('_')

  private def charsWhileNot(fn: Char => Boolean): Parser[String] =
    Parser.charsWhile(!fn(_))

  private val stringNoSpace: Parser[String] =
    charsWhileNot(Set(' '))

  private val stringNoSpaceNoColon: Parser[String] =
    charsWhileNot(Set(' ', ':'))

  private val stringNoSpaceNoColonNoUnderscore: Parser[String] =
    charsWhileNot(Set(' ', ':', '_'))

  private val artifactId: Parser[ArtifactId] =
    for {
      name <- stringNoSpaceNoColonNoUnderscore
      suffix <- (underscore ~ stringNoSpaceNoColon).?
    } yield ArtifactId(name, suffix.map { case (c, str) => name + c + str })

  private val configurations: Parser0[Option[String]] =
    stringNoSpaceNoColon.?.map(_.filterNot(_ === "compile"))

  private val dependency: Parser0[Dependency] =
    for {
      _ <- Parser.string("[INFO]").? ~ wsp.rep0
      groupId <- stringNoSpaceNoColon.map(GroupId.apply) <* colon
      artifactId <- artifactId <* colon <* Parser.string("jar") <* colon
      version <- stringNoSpaceNoColon.map(Version.apply) <* colon
      configurations <- configurations
    } yield Dependency(
      groupId = groupId,
      artifactId = artifactId,
      version = version,
      configurations = configurations
    )

  def parseDependencies(input: List[String]): List[Dependency] =
    input.flatMap(line => dependency.parse(line).toOption.map { case (_, res) => res })

  private val resolver: Parser0[Resolver] =
    for {
      _ <- wsp.rep0 ~ Parser.string("id:") ~ wsp
      id <- stringNoSpace
      _ <- wsp.rep0 ~ Parser.string("url:") ~ wsp
      url <- stringNoSpace
    } yield Resolver.MavenRepository(id, url, None, None)

  def parseResolvers(input: List[String]): List[Resolver] =
    input.mkString.split("""\[INFO]""").toList.flatMap { line =>
      resolver.parse(line).toOption.map { case (_, res) => res }
    }
}
