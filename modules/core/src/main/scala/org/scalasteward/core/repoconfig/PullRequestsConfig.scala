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

package org.scalasteward.core.repoconfig

import cats.implicits._
import cats.{Eq, Monoid}
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec

import java.util.regex.PatternSyntaxException
import scala.util.matching.Regex

final case class PullRequestsConfig(
    frequency: Option[PullRequestFrequency] = None,
    grouping: List[PullRequestGroup] = Nil,
    includeMatchedLabels: Option[Regex] = None,
    customLabels: List[String] = Nil
) {
  def frequencyOrDefault: PullRequestFrequency =
    frequency.getOrElse(PullRequestsConfig.defaultFrequency)
}

object PullRequestsConfig {
  val defaultFrequency: PullRequestFrequency = PullRequestFrequency.Asap

  implicit val pullRequestsConfigEq: Eq[PullRequestsConfig] =
    Eq.fromUniversalEquals

  implicit val pullRequestsConfigConfiguration: Configuration =
    Configuration.default.withDefaults

  implicit val regexCodec: Codec[Regex] =
    Codec
      .from[String](implicitly, implicitly)
      .iemap(s => Either.catchOnly[PatternSyntaxException](s.r).leftMap(_.getMessage))(_.regex)

  implicit val pullRequestsConfigCodec: Codec[PullRequestsConfig] =
    deriveConfiguredCodec

  implicit val pullRequestsConfigMonoid: Monoid[PullRequestsConfig] =
    Monoid.instance(
      PullRequestsConfig(),
      (x, y) =>
        PullRequestsConfig(
          frequency = x.frequency.orElse(y.frequency),
          grouping = x.grouping |+| y.grouping,
          includeMatchedLabels = x.includeMatchedLabels.orElse(y.includeMatchedLabels),
          customLabels = x.customLabels |+| y.customLabels
        )
    )
}
