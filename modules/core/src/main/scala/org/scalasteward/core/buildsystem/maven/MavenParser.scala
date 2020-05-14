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

package org.scalasteward.core.buildsystem.maven

import atto.Atto._
import atto._
import cats.implicits._
import org.scalasteward.core.data._

object MavenParser {
  private val dot: Parser[Char] = char('.')
  private val underscore = char('_')
  private val colon: Parser[Char] = char(':')

  private val group: Parser[GroupId] = (for {
    x <- (many1(noneOf(".:")) ~ opt(dot)).many1
    _ <- colon
  } yield {
    //val parts: NonEmptyList[(NonEmptyList[Char], Option[Char])] = x
    val groupVal = x.toList.map { case (g, d) => (g.toList ++ d.toList).mkString }.mkString
    GroupId(groupVal)
  }).named("group")

  private val version: Parser[String] =
    many1(noneOf(":")).map(_.mkString_(""))

  private val artifact: Parser[ArtifactId] = {
    val artifactString: Parser[String] = many1(noneOf("_ :")).map(_.toList.mkString)
    for {
      init <- artifactString
      restOpt <- opt(underscore ~ artifactString)
      _ <- colon
    } yield ArtifactId(init, restOpt.map { case (sep, suffix) => init + sep + suffix })
  }

  private val configurations: Parser[Option[String]] =
    string("compile").as(None) | string("test").map(Some(_))

  private val parserDependency = for {
    _ <- opt(string("[INFO]") <~ many(whitespace))
    g <- group
    a <- artifact
    _ <- string("jar") <~ colon
    v <- version <~ colon
    c <- configurations
  } yield {
    Dependency(groupId = g, artifactId = a, version = v, configurations = c)
  }

  def parseAllDependencies(input: List[String]): (List[(String, String)], List[Dependency]) =
    input
      .map { line =>
        val either = parserDependency.parse(line).done.either
        either.leftMap(err => line -> err)
      }
      .partitionMap(identity)

  val resolverIdParser: Parser[String] = many1(noneOf(" ")).map(_.toList.mkString)
  val urlParser: Parser[String] = many1(noneOf(" ")).map(_.toList.mkString)

  private val parserResolver = for {
    _ <- many(whitespace)
    _ <- string("id:") ~ whitespace
    id <- resolverIdParser
    _ <- many(whitespace) <~ string("url:") <~ whitespace
    url <- resolverIdParser
    _ <- many(anyChar)
  } yield Resolver.MavenRepository(id.trim, url.trim, None)

  def parseResolvers(raw: String): (List[String], List[Resolver.MavenRepository]) =
    raw
      .split("""\[INFO\]""")
      .toList
      .map(line => parserResolver.parse(line).done.either)
      .partitionMap(identity)
}
