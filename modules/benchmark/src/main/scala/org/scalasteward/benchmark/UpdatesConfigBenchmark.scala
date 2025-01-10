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

package org.scalasteward.benchmark

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit}
import org.scalasteward.core.data.*
import org.scalasteward.core.repoconfig.{UpdatePattern, UpdatesConfig, VersionPattern}
import org.scalasteward.core.util.Nel

@BenchmarkMode(Array(Mode.AverageTime))
class UpdatesConfigBenchmark {

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def keepBench: Any = {
    val groupId = GroupId("org.example")
    val dependency = CrossDependency(Dependency(groupId, ArtifactId("artifact"), Version("1.0.0")))
    val newerVersions = Nel
      .of("2.0.0", "2.1.0", "2.1.1", "2.2.0", "3.0.0", "3.1.0", "3.2.1", "3.3.3", "4.0", "5.0")
      .map(Version.apply)
    val update = Update.ForArtifactId(dependency, newerVersions)

    UpdatesConfig().keep(update)
    UpdatesConfig(allow = Some(List(UpdatePattern(groupId, None, None)))).keep(update)
    UpdatesConfig(allow =
      Some(List(UpdatePattern(groupId, None, Some(VersionPattern(prefix = Some("6.0"))))))
    ).keep(update)
  }
}
