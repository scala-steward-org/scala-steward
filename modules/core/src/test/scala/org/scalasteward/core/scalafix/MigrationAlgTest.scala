package org.scalasteward.core.scalafix

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockState

class MigrationAlgTest extends AnyFunSuite with Matchers {

    
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo
    val scalafmtConf = repoDir / ".scalafix-migrations.conf"
    
    test("loadMigrations on correct file") {
        val migrationsFile = """
        |[{
        |    "groupId": "co.fs2",
        |    "artifactIds": ["fs2-.*"],
        |    "newVersion": "1.0.0",
        |    "rewriteRules": ["github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"]
        |},
        |{"groupId": "com.spotify",
        |    "artifactIds": ["scio-.*"],
        |    "newVersion": "0.7.0",
        |    "rewriteRules": ["github:spotify/scio/FixAvroIO?sha=v0.7.4",
        |    "github:spotify/scio/AddMissingImports?sha=v0.7.4",
        |    "github:spotify/scio/RewriteSysProp?sha=v0.7.4",
        |    "github:spotify/scio/BQClientRefactoring?sha=v0.7.4"]
        |}]""".stripMargin
        val initialState = MockState.empty.add(scalafmtConf, migrationsFile)
        val (_, migrations) = migrationAlg.loadMigrations(repo).run(initialState).unsafeRunSync()

        migrations should contain theSameElementsAs List(Migration(
            GroupId("co.fs2"),
            Nel.of("fs2-.*".r),
            Version("1.0.0"),
            Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5")
          ),
          Migration(
            GroupId("com.spotify"),
            Nel.of("scio-.*".r),
            Version("0.7.0"),
            Nel.of(
              "github:spotify/scio/FixAvroIO?sha=v0.7.4",
              "github:spotify/scio/AddMissingImports?sha=v0.7.4",
              "github:spotify/scio/RewriteSysProp?sha=v0.7.4",
              "github:spotify/scio/BQClientRefactoring?sha=v0.7.4"
            )
          ))
    }

    test("loadMigrations on malformed File") {
        val initialState = MockState.empty.add(scalafmtConf, """{"key": "i'm not a valid Migration"}""")
        val (state, migrations) = migrationAlg.loadMigrations(repo).run(initialState).unsafeRunSync()
        migrations shouldBe empty
        state.logs shouldBe Vector(None -> "Failed to parse migrations file")
    }
}