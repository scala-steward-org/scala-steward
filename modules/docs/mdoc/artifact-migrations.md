# Artifact Migrations

Scala Steward can look for newer versions of artifacts with different group Ids, artifact ids, or both different.

## Adding artifact migrations to Scala Steward

By default, Scala Steward applies the artifact migrations defined in the
[default list][migrations]. When running Scala Steward you can also specify
files or URLs (via the `--artifact-migrations` command-line option) that
contain additional migrations. These files are in [HOCON][HOCON] format and
should look like this:
```hocon
changes = [
  {
    groupIdBefore = com.geirsson
    groupIdAfter = org.scalameta
    artifactIdAfter = sbt-scalafmt
    initialVersion = 2.0.0
  }
]
```
In this example, scala-steward will look in the project for dependencies with the before group id, "com.geirsson", and 
artifact id "sbt-scalafmt". If found, scala-steward will search for updates using the after group id, "org.scalameta", 
artifact id "sbt-scalafmt", and version greater than the initial version, 2.0.0. If found, scala-steward will update
to the after group id and latest version.

The fields `groupIdBefore` and `artifactIdBefore` are optional. If just `groupIdBefore` is specified, as in the previous
example, then only the group id will get renamed. If just `artifactIdBefore` is specified, then only the artifact id
will get renamed. Specifying both `groupIdBefore` and `artifactIdBefore` will rename both. The `initialVersion` needs
to be greater than the latest version with the old groupId / old artifact id.

Pull requests that added artifact migrations can be found [here][migration-prs].

[migrations]: @GITHUB_URL@/blob/@MAIN_BRANCH@/modules/core/src/main/resources/artifact-migrations.conf
[migration-prs]: @GITHUB_URL@/pulls?q=label%3Aartifact-migration
[HOCON]: https://github.com/lightbend/config/blob/master/HOCON.md
