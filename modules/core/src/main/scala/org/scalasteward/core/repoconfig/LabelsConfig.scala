/*
 * Copyright 2018-2019 scala-steward contributors
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

import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveDecoder
import org.scalasteward.core.model._

final case class LabelsConfig(
    library: List[LabelPattern] = List.empty,
    testLibrary: List[LabelPattern] = List.empty,
    sbtPlugin: List[LabelPattern] = List.empty
) {
  def getLabels(update: Update.Single): List[Label] = {
    val maybeLibraryLabel = maybeLabel(library, update, Label.LibraryUpdate)
    val maybeTestLibraryLabel = maybeLabel(testLibrary, update, Label.TestLibraryUpdate)
    val maybeSbtPluginLabel = maybeLabel(sbtPlugin, update, Label.SbtPluginUpdate)

    List(
      maybeLibraryLabel,
      maybeTestLibraryLabel,
      maybeSbtPluginLabel
    ).flatten
  }

  private def maybeLabel(
      patterns: List[LabelPattern],
      update: Update.Single,
      label: Label
  ): Option[Label] =
    Option(LabelPattern.doesMatch(patterns, update)).collect {
      case true => label
    }

}

object LabelsConfig {
  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  implicit val labelsConfigDecoder: Decoder[LabelsConfig] =
    deriveDecoder
}
