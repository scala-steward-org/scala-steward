import scala.util.Properties
import scala.reflect.io.Path
import com.typesafe.sbt.packager.docker._
import sbtcrossproject.{CrossProject, CrossType, Platform}
import org.typelevel.sbt.gha.JavaSpec.Distribution.Temurin

/// variables

val groupId = "org.scala-steward"
val projectName = "scala-steward"
val rootPkg = groupId.replace("-", "")
val gitHubOwner = "scala-steward-org"
val gitHubUrl = s"https://github.com/$gitHubOwner/$projectName"
val mainBranch = "main"
val gitHubUserContent = s"https://raw.githubusercontent.com/$gitHubOwner/$projectName/$mainBranch"

val moduleCrossPlatformMatrix: Map[String, List[Platform]] = Map(
  "benchmark" -> List(JVMPlatform),
  "core" -> List(JVMPlatform),
  "docs" -> List(JVMPlatform),
  "dummy" -> List(JVMPlatform)
)

val Scala213 = "2.13.12"

/// sbt-typelevel configuration

ThisBuild / crossScalaVersions := Seq(Scala213)
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch(mainBranch)),
  RefPredicate.StartsWith(Ref.Tag("v"))
)
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Run(
    List("sbt ci-release"),
    name = Some("Publish JARs"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  ),
  WorkflowStep.Run(
    List(
      "docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}",
      "sbt core/Docker/publish"
    ),
    name = Some("Publish Docker image")
  )
)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec(Temurin, "17"), JavaSpec(Temurin, "11"))
ThisBuild / githubWorkflowBuild :=
  Seq(
    WorkflowStep.Sbt(List("validate"), name = Some("Build project")),
    WorkflowStep.Use(
      UseRef.Public("codecov", "codecov-action", "v3"),
      name = Some("Codecov")
    )
  )

ThisBuild / mergifyPrRules := {
  val authorCondition = MergifyCondition.Custom("author=scala-steward")
  Seq(
    MergifyPrRule(
      "label scala-steward's PRs",
      List(authorCondition),
      List(MergifyAction.Label(List("dependency-update")))
    ),
    MergifyPrRule(
      "merge scala-steward's PRs",
      List(authorCondition) ++ mergifySuccessConditions.value,
      List(MergifyAction.Merge(Some("squash")))
    )
  )
}

/// global build settings

ThisBuild / dynverSeparator := "-"

ThisBuild / evictionErrorLevel := Level.Info

/// projects

lazy val root = project
  .in(file("."))
  .aggregate(benchmark.jvm, core.jvm, docs.jvm, dummy.jvm)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val benchmark = myCrossProject("benchmark")
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(noPublishSettings)
  .settings(
    scalacOptions -= "-Wnonunit-statement",
    coverageEnabled := false,
    unusedCompileDependencies := Set.empty
  )

lazy val core = myCrossProject("core")
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.bcprovJdk15to18,
      Dependencies.betterFiles,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsParse,
      Dependencies.circeConfig,
      Dependencies.circeGeneric,
      Dependencies.circeGenericExtras,
      Dependencies.circeParser,
      Dependencies.circeRefined,
      Dependencies.commonsIo,
      Dependencies.coursierCore,
      Dependencies.coursierSbtMaven,
      Dependencies.cron4sCore,
      Dependencies.decline,
      Dependencies.fs2Core,
      Dependencies.fs2Io,
      Dependencies.gitignore,
      Dependencies.http4sCirce,
      Dependencies.http4sClient,
      Dependencies.http4sCore,
      Dependencies.http4sJdkhttpClient,
      Dependencies.jjwtApi,
      Dependencies.jjwtImpl % Runtime,
      Dependencies.jjwtJackson % Runtime,
      Dependencies.log4catsSlf4j,
      Dependencies.monocleCore,
      Dependencies.refined,
      Dependencies.scalacacheCaffeine,
      Dependencies.logbackClassic % Runtime,
      Dependencies.catsLaws % Test,
      Dependencies.circeLiteral % Test,
      Dependencies.disciplineMunit % Test,
      Dependencies.http4sDsl % Test,
      Dependencies.http4sEmberServer % Test,
      Dependencies.munit % Test,
      Dependencies.munitCatsEffect % Test,
      Dependencies.munitScalacheck % Test,
      Dependencies.refinedScalacheck % Test,
      Dependencies.scalacheck % Test
    ),
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "versions", "9", "module-info.class") =>
        // (core / assembly) deduplicate: different file contents found in the following:
        // https/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.4.20/kotlin-stdlib-1.4.20.jar:META-INF/versions/9/module-info.class
        // https/repo1.maven.org/maven2/org/tukaani/xz/1.9/xz-1.9.jar:META-INF/versions/9/module-info.class
        MergeStrategy.first
      case PathList("module-info.class") =>
        // (core / assembly) deduplicate: different file contents found in the following:
        // https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.12.6/jackson-annotations-2.12.6.jar:module-info.class
        // https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.12.6/jackson-core-2.12.6.jar:module-info.class
        // https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.12.6.1/jackson-databind-2.12.6.1.jar:module-info.class
        MergeStrategy.discard
      case PathList("META-INF", "sisu", "javax.inject.Named") =>
        // (core / assembly) deduplicate: different file contents found in the following:
        // https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-archiver/4.5.0/plexus-archiver-4.5.0.jar:META-INF/sisu/javax.inject.Named
        // https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-io/3.4.0/plexus-io-3.4.0.jar:META-INF/sisu/javax.inject.Named
        MergeStrategy.first
      case otherwise =>
        val defaultStrategy = (assembly / assemblyMergeStrategy).value
        defaultStrategy(otherwise)
    },
    buildInfoKeys := Seq[BuildInfoKey](
      organization,
      version,
      scalaVersion,
      scalaBinaryVersion,
      sbtVersion,
      BuildInfoKey("gitHubUrl" -> gitHubUrl),
      BuildInfoKey("gitHubUserContent" -> gitHubUserContent),
      BuildInfoKey("mainBranch" -> mainBranch),
      BuildInfoKey.map(git.gitHeadCommit) { case (k, v) => k -> v.getOrElse(mainBranch) },
      BuildInfoKey("millPluginArtifactName" -> Dependencies.scalaStewardMillPluginArtifactName),
      BuildInfoKey("millPluginVersion" -> Dependencies.scalaStewardMillPlugin.revision)
    ),
    buildInfoPackage := moduleRootPkg.value,
    initialCommands += s"""
      import ${moduleRootPkg.value}._
      import ${moduleRootPkg.value}.data._
      import ${moduleRootPkg.value}.util._
      import better.files.File
      import cats.effect.IO
      import org.http4s.client.Client
      import org.typelevel.log4cats.Logger
      import org.typelevel.log4cats.slf4j.Slf4jLogger

      implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
    """,
    // Inspired by https://stackoverflow.com/a/41978937/460387
    Test / sourceGenerators += Def.task {
      val file = (Test / sourceManaged).value / "InitialCommandsTest.scala"
      val content =
        s"""object InitialCommandsTest {
           |  ${initialCommands.value}
           |  // prevent warnings
           |  intellijThisImportIsUsed(Client); intellijThisImportIsUsed(File);
           |  intellijThisImportIsUsed(Nel); intellijThisImportIsUsed(Repo);
           |  intellijThisImportIsUsed(Main);
           |}""".stripMargin
      IO.write(file, content)
      Seq(file)
    }.taskValue,
    run / fork := true,
    // Uncomment for remote debugging:
    // run / javaOptions += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
    Test / fork := true,
    Compile / resourceGenerators += Def.task {
      val outDir = (Compile / resourceManaged).value
      def downloadPlugin(v: String): File = {
        val outFile = outDir / s"StewardPlugin_$v.scala"
        if (!outFile.exists()) {
          val u =
            s"https://raw.githubusercontent.com/scala-steward-org/sbt-plugin/main/modules/sbt-plugin-$v/src/main/scala/org/scalasteward/sbt/plugin/StewardPlugin_$v.scala"
          val content = scala.util.Using(scala.io.Source.fromURL(u))(_.mkString).get
          IO.write(outFile, content)
        }
        outFile
      }
      Seq(downloadPlugin("1_0_0"), downloadPlugin("1_3_11"))
    }.taskValue
  )

lazy val docs = myCrossProject("docs")
  .dependsOn(core)
  .enablePlugins(MdocPlugin)
  .settings(noPublishSettings)
  .settings(
    mdocIn := baseDirectory.value / ".." / "mdoc",
    mdocOut := (LocalRootProject / baseDirectory).value / "docs",
    mdocVariables := Map(
      "GITHUB_URL" -> gitHubUrl,
      "MAIN_BRANCH" -> mainBranch
    ),
    checkDocs := {
      val inDir = mdocIn.value.getCanonicalPath
      val outDir = mdocOut.value.getCanonicalPath
      val rootDir = (LocalRootProject / baseDirectory).value
      try git.runner.value.apply("diff", "--quiet", outDir)(rootDir, streams.value.log)
      catch {
        case t: Throwable =>
          val diff = git.runner.value.apply("diff", outDir)(rootDir, streams.value.log)
          val msg = s"""|Docs in $inDir and $outDir are out of sync.
                        |Run 'sbt docs/mdoc' and commit the changes to fix this.
                        |The diff is:
                        |$diff
                        |""".stripMargin
          throw new Throwable(msg, t)
      }
      ()
    },
    coverageEnabled := false,
    unusedCompileDependencies := Set.empty
  )

// Dummy project to receive updates from @scala-steward for this project's
// libraryDependencies.
lazy val dummy = myCrossProject("dummy")
  .disablePlugins(ExplicitDepsPlugin)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.scalaStewardMillPlugin
    )
  )

/// settings

def myCrossProject(name: String): CrossProject =
  CrossProject(name, file(name))(moduleCrossPlatformMatrix(name): _*)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file(s"modules/$name"))
    .settings(
      moduleName := s"$projectName-$name",
      moduleRootPkg := s"$rootPkg.${name.replace('-', '.')}"
    )
    .settings(commonSettings)

lazy val commonSettings = Def.settings(
  compileSettings,
  metadataSettings,
  scaladocSettings
)

lazy val compileSettings = Def.settings(
  scalaVersion := Scala213,
  // Uncomment for local development:
  // scalacOptions -= "-Xfatal-warnings",
  doctestTestFramework := DoctestTestFramework.Munit
)

lazy val metadataSettings = Def.settings(
  name := projectName,
  organization := groupId,
  homepage := Some(url(gitHubUrl)),
  startYear := Some(2018),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(ScmInfo(homepage.value.get, s"scm:git:$gitHubUrl.git")),
  headerLicense := Some(HeaderLicense.ALv2("2018-2023", "Scala Steward contributors")),
  developers := List(
    Developer(
      id = "alejandrohdezma",
      name = "Alejandro Hernández",
      email = "",
      url = url("https://github.com/alejandrohdezma")
    ),
    Developer(
      id = "exoego",
      name = "TATSUNO Yasuhiro",
      email = "",
      url = url("https://github.com/exoego")
    ),
    Developer(
      id = "fthomas",
      name = "Frank S. Thomas",
      email = "",
      url = url("https://github.com/fthomas")
    ),
    Developer(
      id = "mzuehlke",
      name = "Marco Zühlke",
      email = "",
      url = url("https://github.com/mzuehlke")
    )
  )
)

lazy val dockerSettings = Def.settings(
  dockerBaseImage := Option(System.getenv("DOCKER_BASE_IMAGE"))
    .getOrElse("eclipse-temurin:11-alpine"),
  dockerCommands ++= {
    val curl = "curl -fL --output"
    val binDir = "/usr/local/bin"
    val sbtVer = sbtVersion.value
    val sbtTgz = s"sbt-$sbtVer.tgz"
    val installSbt = Seq(
      s"$curl $sbtTgz https://github.com/sbt/sbt/releases/download/v$sbtVer/$sbtTgz",
      s"tar -xf $sbtTgz",
      s"rm -f $sbtTgz"
    ).mkString(" && ")
    val millVer = Dependencies.millScriptVersion
    val millBin = s"$binDir/mill"
    val releasePageVersion = millVer.split("-") match {
      case Array(v, m, _*) if m.startsWith("M") => s"${v}-${m}"
      case Array(v, _*)                         => v
    }
    val installMill = Seq(
      s"$curl $millBin https://github.com/lihaoyi/mill/releases/download/${releasePageVersion}/$millVer",
      s"chmod +x $millBin"
    ).mkString(" && ")
    val csBin = s"$binDir/cs"
    val installCoursier = Seq(
      s"$curl $csBin.gz https://github.com/coursier/coursier/releases/download/v${Dependencies.coursierCore.revision}/cs-x86_64-pc-linux-static.gz",
      s"gunzip $csBin.gz",
      s"chmod +x $csBin"
    ).mkString(" && ")
    val scalaCliBin = s"$binDir/scala-cli"
    val installScalaCli = Seq(
      s"$curl $scalaCliBin.gz https://github.com/Virtuslab/scala-cli/releases/latest/download/scala-cli-x86_64-pc-linux-static.gz",
      s"gunzip $scalaCliBin.gz",
      s"chmod +x $scalaCliBin"
    ).mkString(" && ")
    Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk --no-cache add bash git ca-certificates curl maven openssh nodejs npm"),
      Cmd("RUN", installSbt),
      Cmd("RUN", installMill),
      Cmd("RUN", installCoursier),
      Cmd("RUN", installScalaCli),
      Cmd("RUN", s"$csBin install --install-dir $binDir scalafix scalafmt"),
      Cmd("RUN", "npm install --global yarn"),
      // Ensure binaries are in PATH
      Cmd("RUN", "echo $PATH"),
      Cmd("RUN", "which cs mill mvn node npm sbt scala-cli scalafix scalafmt yarn")
    )
  },
  Docker / packageName := s"fthomas/${name.value}",
  dockerUpdateLatest := true,
  dockerAliases ++= {
    if (!isSnapshot.value) Seq(dockerAlias.value.withTag(Option("latest-release"))) else Nil
  },
  dockerEnvVars := Map(
    "PATH" -> "/opt/docker/sbt/bin:${PATH}",
    "COURSIER_PROGRESS" -> "false"
  )
)

lazy val noPublishSettings = Def.settings(
  publish / skip := true
)

lazy val scaladocSettings = Def.settings(
  Compile / doc / scalacOptions ++= {
    val tag = s"v${version.value}"
    val tree = if (isSnapshot.value) git.gitHeadCommit.value.getOrElse(mainBranch) else tag
    Seq(
      "-doc-source-url",
      s"${scmInfo.value.get.browseUrl}/blob/$tree€{FILE_PATH}.scala",
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath
    )
  }
)

/// setting keys

lazy val checkDocs = taskKey[Unit]("")

lazy val moduleRootPkg = settingKey[String]("").withRank(KeyRanks.Invisible)
moduleRootPkg := rootPkg

// Run Scala Steward from sbt for development and testing.
// Do not do this in production.
lazy val runSteward = taskKey[Unit]("")
runSteward := Def.taskDyn {
  val home = System.getenv("HOME")
  val projectDir = (LocalRootProject / baseDirectory).value
  val args = Seq(
    Seq("--workspace", s"$projectDir/workspace"),
    Seq("--repos-file", s"$projectDir/repos.md"),
    Seq("--git-author-email", s"me@$projectName.org"),
    Seq("--forge-login", projectName),
    Seq("--git-ask-pass", s"$home/.github/askpass/$projectName.sh"),
    Seq("--whitelist", s"$home/.cache/coursier"),
    Seq("--whitelist", s"$home/.cache/JNA"),
    Seq("--whitelist", s"$home/.cache/mill"),
    Seq("--whitelist", s"$home/.ivy2"),
    Seq("--whitelist", s"$home/.m2"),
    Seq("--whitelist", s"$home/.mill"),
    Seq("--whitelist", s"$home/.sbt")
  ).flatten.mkString(" ", " ", "")
  (core.jvm / Compile / run).toTask(args)
}.value

/// commands

def addCommandsAlias(name: String, cmds: Seq[String]) =
  addCommandAlias(name, cmds.mkString(";", ";", ""))

addCommandsAlias(
  "validate",
  Seq(
    "clean",
    "headerCheck",
    "scalafmtCheckAll",
    "scalafmtSbtCheck",
    "unusedCompileDependenciesTest",
    "coverage",
    "test",
    "coverageReport",
    "doc",
    "docs/mdoc",
    "docs/checkDocs",
    "package",
    "packageSrc",
    "core/assembly",
    "Docker/publishLocal"
  )
)

addCommandsAlias(
  "fmt",
  Seq(
    "headerCreate",
    "scalafmtAll",
    "scalafmtSbt"
  )
)
