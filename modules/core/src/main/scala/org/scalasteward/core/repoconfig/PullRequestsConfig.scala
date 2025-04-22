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

package org.scalasteward.core.repoconfig

import cats.implicits.*
import cats.{Eq, Monoid}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import java.util.regex.PatternSyntaxException
import scala.util.matching.Regex

final case class PullRequestsConfig(
    frequency: Option[PullRequestFrequency] = None,
    private val grouping: Option[List[PullRequestGroup]] = None,
    includeMatchedLabels: Option[Regex] = None,
    private val customLabels: Option[List[String]] = None,
    draft: Option[Boolean] = None
) {
  def frequencyOrDefault: PullRequestFrequency =
    frequency.getOrElse(PullRequestsConfig.defaultFrequency)

  def groupingOrDefault: List[PullRequestGroup] =
    grouping.getOrElse(Nil)

  def customLabelsOrDefault: List[String] =
    customLabels.getOrElse(Nil)
}

object PullRequestsConfig {
  val defaultFrequency: PullRequestFrequency = PullRequestFrequency.Asap

  implicit val pullRequestsConfigEq: Eq[PullRequestsConfig] =
    Eq.fromUniversalEquals

  implicit val regexCodec: Codec[Regex] =
    Codec
      .from[String](implicitly, implicitly)
      .iemap(s => Either.catchOnly[PatternSyntaxException](s.r).leftMap(_.getMessage))(_.regex)

  implicit val pullRequestsConfigCodec: Codec[PullRequestsConfig] =
    deriveCodec

  implicit val pullRequestsConfigMonoid: Monoid[PullRequestsConfig] =
    Monoid.instance(
      PullRequestsConfig(),
      (x, y) =>
        PullRequestsConfig(
          frequency = x.frequency.orElse(y.frequency),
          grouping = x.grouping |+| y.grouping,
          includeMatchedLabels = x.includeMatchedLabels.orElse(y.includeMatchedLabels),
          customLabels = x.customLabels |+| y.customLabels,
          draft = (x.draft, y.draft).mapN(_ || _)
        )
    )
}
