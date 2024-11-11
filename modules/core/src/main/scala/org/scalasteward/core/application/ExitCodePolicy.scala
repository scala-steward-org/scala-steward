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

package org.scalasteward.core.application

import cats.effect.ExitCode

trait ExitCodePolicy {
  def exitCodeFor(runResults: RunResults): ExitCode
}

object ExitCodePolicy {
  private def successIf(isSuccess: RunResults => Boolean): ExitCodePolicy =
    (runResults: RunResults) => if (isSuccess(runResults)) ExitCode.Success else ExitCode.Error

  val SuccessIfAnyRepoSucceeds: ExitCodePolicy = successIf(_.successRepos.nonEmpty)

  val SuccessOnlyIfAllReposSucceed: ExitCodePolicy = successIf(_.reposWithFailures.isEmpty)
}
