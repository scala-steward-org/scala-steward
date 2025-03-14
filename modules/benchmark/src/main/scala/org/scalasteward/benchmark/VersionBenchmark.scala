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
import org.scalasteward.core.data.Version.Component

@BenchmarkMode(Array(Mode.AverageTime))
class VersionBenchmark {
  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def parseBench: Any = {
    Component.parse("1.1.2-1")
    Component.parse("8.0.192-R14")
    Component.parse("1.2.0+9-4a769501")
    Component.parse("1.0.0-20201119-091040")
  }
}
