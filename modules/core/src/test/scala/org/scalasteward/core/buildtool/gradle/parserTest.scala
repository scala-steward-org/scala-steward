package org.scalasteward.core.buildtool.gradle

//import org.scalasteward.core.TestSyntax._
//import org.scalasteward.core.data.ArtifactId
// import org.scalasteward.core.data.Resolver.MavenRepository
import better.files.File
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState

class parserTest extends AnyFunSuite with Matchers {
  test("parseDependencies") {
    val tempLockFile = File.temp / "dependencies.lock"
    val input =
      s"""|Configuration on demand is an incubating feature.
         |[1] include boson-algo-commons-testing
         |[2] include boson-autoapp:boson-autoapp_2.1_2.11
         |[2] include boson-autoapp_2.4_2.11:boson-autoapp_2.4_2.11
         |[3] include boson-bandit:boson-bandit_2.1_2.11
         |[3] include boson-bandit_2.4_2.11:boson-bandit_2.4_2.11
         |[4] include boson-cinder:boson-cinder_2.1_2.11
         |[4] include boson-cinder_2.4_2.11:boson-cinder_2.4_2.11
         |[5] include boson-core:boson-core_2.1_2.11
         |[5] include boson-core_2.4_2.11:boson-core_2.4_2.11
         |[6] include boson-dts:boson-dts_2.1_2.11
         |[6] include boson-dts_2.4_2.11:boson-dts_2.4_2.11
         |[7] include boson-hermes:boson-hermes_2.1_2.11
         |[7] include boson-hermes_2.4_2.11:boson-hermes_2.4_2.11
         |[8] include boson-imphist:boson-imphist_2.1_2.11
         |[8] include boson-imphist_2.4_2.11:boson-imphist_2.4_2.11
         |[9] include boson-logging:boson-logging_2.1_2.11
         |[9] include boson-logging_2.4_2.11:boson-logging_2.4_2.11
         |[10] include boson-macros
         |[11] include boson-monitoring:boson-monitoring_2.1_2.11
         |[11] include boson-monitoring_2.4_2.11:boson-monitoring_2.4_2.11
         |[12] include boson-online:boson-online_2.1_2.11
         |[12] include boson-online_2.4_2.11:boson-online_2.4_2.11
         |[13] include boson-platform:boson-platform_2.1_2.11
         |[14] include boson-python:boson-python_2.1_2.11
         |[14] include boson-python_2.4_2.11:boson-python_2.4_2.11
         |[15] include boson-remote:boson-remote_2.1_2.11
         |[15] include boson-remote_2.4_2.11:boson-remote_2.4_2.11
         |[16] include boson-sampling:boson-sampling_2.1_2.11
         |[16] include boson-sampling_2.4_2.11:boson-sampling_2.4_2.11
         |[17] include boson-snapshot_2.1_2.11:boson-snapshot_2.1_2.11
         |[17] include boson-snapshot_2.4_2.11:boson-snapshot_2.4_2.11
         |[18] include boson-spark-compat_2.1_2.11:boson-spark-compat_2.1_2.11
         |[18] include boson-spark-compat_2.4_2.11:boson-spark-compat_2.4_2.11
         |[19] include boson-spark-io:boson-spark-io_2.1_2.11
         |[19] include boson-spark-io_2.4_2.11:boson-spark-io_2.4_2.11
         |[20] include boson-tensorflow:boson-tensorflow_2.1_2.11
         |[20] include boson-tensorflow_2.4_2.11:boson-tensorflow_2.4_2.11
         |[21] include boson-testing:boson-testing_2.1_2.11
         |[21] include boson-testing_2.4_2.11:boson-testing_2.4_2.11
         |[22] include boson-tracker:boson-tracker_2.1_2.11
         |[22] include boson-tracker_2.4_2.11:boson-tracker_2.4_2.11
         |[23] include boson-turbo:boson-turbo_2.1_2.11
         |[23] include boson-turbo_2.4_2.11:boson-turbo_2.4_2.11
         |[24] include boson-viz:boson-viz_2.1_2.11
         |[24] include boson-viz_2.4_2.11:boson-viz_2.4_2.11
         |[25] include boson-viz-display:boson-viz-display_2.1_2.11
         |[25] include boson-viz-display_2.4_2.11:boson-viz-display_2.4_2.11
         |[26] include boson-vms:boson-vms_2.1_2.11
         |[26] include boson-vms_2.4_2.11:boson-vms_2.4_2.11
         |[27] include boson-xgboost:boson-xgboost_2.1_2.11
         |[27] include boson-xgboost_2.4_2.11:boson-xgboost_2.4_2.11
         |Remote build cache is disabled when running with --offline.
         |
         |> Configure project :
         |+===============================================================================
         || Nebula 8.6.6 on Gradle 7.4.2
         || Java version: OpenJDK 1.8.0_322-zulu (build 1.8.0_322-b06)
         || Project: boson_2.11
         |+=============================================================================== 
         |> Task :boson-vms_2.4_2.11:stewardDependencies
         |repositories
         |name: nfrepo-everything-pomMavenRepo
         |url: https://artifacts.netflix.net/nfrepo-everything-pom
         |name: nfrepo-everything-pomGradleMetadataMavenRepo
         |url: https://artifacts.netflix.net/nfrepo-everything-pom
         |name: nfrepo-everything-pomForModulesWithoutMetadata
         |url: https://artifacts.netflix.net/nfrepo-everything-pom
         |name: nfrepo-everythingRepo
         |url: https://artifacts.netflix.net/nfrepo-everything
         |name: nfrepo-everythingWithoutMetadataRepo
         |url: https://artifacts.netflix.net/nfrepo-everything
         |name: local
         |url: file:/Users/jvican/ivy2-local
         |dependency-lock-file
         |${tempLockFile.path}
         |
         |> Task :boson-xgboost_2.1_2.11:stewardDependencies
         |repositories
         |name: nfrepo-everything-pomMavenRepo
         |url: https://artifacts.netflix.net/nfrepo-everything-pom
         |name: nfrepo-everything-pomGradleMetadataMavenRepo
         |url: https://artifacts.netflix.net/nfrepo-everything-pom
         |name: nfrepo-everything-pomForModulesWithoutMetadata
         |url: https://artifacts.netflix.net/nfrepo-everything-pom
         |name: nfrepo-everythingRepo
         |url: https://artifacts.netflix.net/nfrepo-everything
         |name: nfrepo-everythingWithoutMetadataRepo
         |url: https://artifacts.netflix.net/nfrepo-everything
         |name: local
         |url: file:/Users/jvican/ivy2-local
         |dependency-lock-file
         |${tempLockFile.path}
         |
         |BUILD SUCCESSFUL in 2s
         |52 actionable tasks: 52 executed
         |The build is running offline. A build scan will not be published at this time, but it can be published if you run the buildScanPublishPrevious task in the next build.
         |""".stripMargin.linesIterator.toList

    val parseDependencies = fileAlg.createTemporarily(tempLockFile, "") {
      for {
        dependencyLockContents <- fileAlg.readResource("gradle-dependencies.lock")
        _ <- fileAlg.writeFile(tempLockFile, dependencyLockContents)
        dependencies <- parser.parseDependencies(input)
      } yield {
        dependencies
      }
    }

    parseDependencies
      .runS(MockState.empty)
      .unsafeRunSync()
  }
}
