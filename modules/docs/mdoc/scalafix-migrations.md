# Scalafix Migrations

Scala Steward can run [Scalafix][Scalafix] rewrite rules for version updates
to not only bump version numbers but to also adapt the code for the new
version. This allows to automatically rewrite deprecated code or code that
would not compile with the new version of the dependency.

Here are two pull requests that demonstrate this feature:

* https://github.com/coursier/versions/pull/25
* https://github.com/barambani/http4s-extend/pull/67/files

More pull requests where Scala Steward applied Scalafix migrations can be found here:

* [Created pull requests](https://github.com/search?q=author%3Ascala-steward+is%3Apr+scalafix-migrations&type=pullrequests)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Apr+scalafix-migrations))
* [Merged pull requests]( https://github.com/search?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc+scalafix-migrations&type=pullrequests)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc+scalafix-migrations))

## How does this work?

Scala Steward contains a list of [migration rules][migrations] combined with
metadata that describes to which updates they are applicable. When it prepares
a pull request and finds that one or more rules match the current update,
Scala Steward applies these migration rules via the sbt-scalafix plugin
which resolves those rules and edits the source code accordingly.

## Writing migration rules for your project

See the [Scalafix Developer Guide][scalafix-dev-guide] for more information
about writing rewrite rules or have a look at the existing
[migration rules][migrations] for inspiration. Rules in Scala Steward must be
accessible via the [github:][using-github], the [http:][using-http], or the
[dependency:][using-dependency] schemes.

## Adding migration rules to Scala Steward

After you have written a new migration rule for a new version of your project,
Scala Steward needs to be made aware of it. Creating a pull request that adds
the new rule to the list of [migrations][migrations] is enough for that. Once
that pull request is merged, all Scala Steward instances will start using this
migration.

When running Scala Steward you can also specify files or URLs (via the
`--scalafix-migrations` command-line option) that contain additional
migrations which are not present in the [default list][migrations].
These files are in [HOCON][HOCON] format and should look like this:
```hocon
migrations = [
  {
    groupId: "org.http4s",
    artifactIds: ["http4s-.*"],
    newVersion: "0.20.0",
    rewriteRules: ["github:http4s/http4s/v0_20?sha=v0.20.11"],
    doc: "https://github.com/http4s/http4s/blob/v0.20.0/docs/src/main/tut/upgrading.md"
  }
]
```

Pull requests that added migration rules can be found [here][scalafix-prs].

[Scalafix]: https://scalacenter.github.io/scalafix/
[migrations]: @GITHUB_URL@/blob/@MAIN_BRANCH@/modules/core/src/main/resources/scalafix-migrations.conf
[scalafix-dev-guide]: https://scalacenter.github.io/scalafix/docs/developers/setup.html
[using-dependency]: https://scalacenter.github.io/scalafix/docs/rules/external-rules.html
[using-github]: https://scalacenter.github.io/scalafix/docs/developers/tutorial.html#using-github
[using-http]: https://scalacenter.github.io/scalafix/docs/developers/tutorial.html#using-http
[scalafix-prs]: @GITHUB_URL@/pulls?q=label%3Ascalafix-migration
[HOCON]: https://github.com/lightbend/config/blob/master/HOCON.md
