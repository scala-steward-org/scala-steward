# Artifact Migrations

Scala Steward can not only bump versions but also change groupId and/or artifactIds if they have been renamed.

Here is a pull request that demonstrate this feature:

* https://github.com/scala-steward-org/scala-steward/pull/2264/files

More pull requests where Scala Steward applied artifact migrations can be found here:

* [Created pull requests](https://github.com/search?q=author%3Ascala-steward+is%3Apr+artifact-migrations&type=pullrequests)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Apr+artifact-migrations))
* [Merged pull requests]( https://github.com/search?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc+artifact-migrations&type=pullrequests)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc+artifact-migrations))

## How does this work?

Scala Steward contains a list of [artifact migrations][migrations] that maps old groupIds and/or artifactIds to their new values.
When an artifact migration for a dependency exists, Scala Steward will look for new versions with the old **and** new groupIds and artifactIds.
If it finds a suitable newer version with the new groupId/artifactId, it will consider it an update of that dependency and change the groupId/artifactId accordingly.

## Adding artifact migrations to Scala Steward

Artifact migrations of public dependencies should be added to the [default list of migrations][migrations] via pull requests.
Once a pull request with a migration is merged, all Scala Steward instances will start using this migration.

When running Scala Steward you can also specify files or URLs (via the `--artifact-migrations` command-line option) that contain additional migrations which are not present in the default list.
These files are in [HOCON][HOCON] format and should look like this:
```hocon
changes = [
  {
    groupIdBefore = com.geirsson
    groupIdAfter = org.scalameta
    artifactIdAfter = sbt-scalafmt
  }
]
```
In this example, Scala Steward will change `com.geirsson:sbt-scalafmt` to `org.scalameta:sbt-scalafmt`.

The fields `groupIdBefore` and `artifactIdBefore` are optional while `groupIdAfter` and `artifactIdAfter` are mandatory.
If just `groupIdBefore` is specified, as in the example above, only the groupId will be changed.
If just `artifactIdBefore` is specified, only the artifactId will be changed.
If `groupIdBefore` and `artifactIdBefore` are specified, both, groupId and artifactId, are changed.

Pull requests that added artifact migrations can be found [here][migration-prs].

[migrations]: https://github.com/scala-steward-org/scala-steward/blob/main/modules/core/src/main/resources/artifact-migrations.v2.conf
[migration-prs]: https://github.com/scala-steward-org/scala-steward/pulls?q=label%3Aartifact-migration
[HOCON]: https://github.com/lightbend/config/blob/master/HOCON.md
