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
import org.openjdk.jmh.annotations.*
import org.scalasteward.core.data.Version
import org.scalasteward.core.edit.update.VersionPositionScanner
import org.scalasteward.core.io.FileData

@BenchmarkMode(Array(Mode.AverageTime))
class VersionPositionScannerBenchmark {

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def findPositionsBench(myState: MyState): Any =
    VersionPositionScanner.findPositions(Version("1.0.0"), myState.fileData)
}

@State(Scope.Benchmark)
class MyState {
  val fileData: FileData =
    FileData(
      "",
      """ val name: String = "foo"
        | val version: String = "1.0.0"
        | "groupId:artifactId:1.0.0"
        | 1.0.0
        | "foo" % "bar" % "1.0.0"
        | "foo-bar-baz" :
        | "" : "1.0.0"
        |  // val version = "1.0.0"
        |""".stripMargin
    )
}
