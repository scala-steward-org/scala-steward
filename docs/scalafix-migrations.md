# Scalafix Migrations

Scala Steward can run [Scalafix][Scalafix] rewrite rules for version updates
to not only bump version numbers but to also adapt the code for the new
version. This allows to automatically rewrite deprecated code or code that
would not compile with the new version of the dependency.

Here are two pull requests that demonstrate this feature:
 * https://github.com/barambani/http4s-extend/pull/67/files
 * https://github.com/fthomas/scalafix-test/pull/6/files

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
accessible via the [github:][using-github] or [http:][using-http] schemes.

## Adding migration rules to Scala Steward

After you have written a new migration rule for a new version of your project,
Scala Steward needs to be made aware of it. Creating a pull request that adds
the new rule to the list of [migrations][migrations] is enough for that. Once
that pull request is merged, Scala Steward will start using this migration.

Pull requests that added migration rules can be found [here][scalafix-prs].

[Scalafix]: https://scalacenter.github.io/scalafix/
[migrations]: https://github.com/fthomas/scala-steward/blob/master/modules/core/src/main/scala/org/scalasteward/core/scalafix/package.scala
[scalafix-dev-guide]: https://scalacenter.github.io/scalafix/docs/developers/setup.html
[using-github]: https://scalacenter.github.io/scalafix/docs/developers/sharing-rules.html#using-github
[using-http]: https://scalacenter.github.io/scalafix/docs/developers/sharing-rules.html#using-http
[scalafix-prs]: https://github.com/fthomas/scala-steward/pulls?q=label%3Ascalafix-migration
