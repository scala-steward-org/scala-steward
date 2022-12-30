package org.scalasteward.benchmark

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
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
