# Changelog

## v0.3.0 (10/07/2019)

#### Enhancements

- Add heuristic that uses words from the groupId [#545](https://github.com/fthomas/scala-steward/pull/545) by [@fthomas](https://github.com/fthomas)
- Only try to edit files that contain the current version [#541](https://github.com/fthomas/scala-steward/pull/541) by [@fthomas](https://github.com/fthomas)
- Add new replaceAllInSliding strategy [#527](https://github.com/fthomas/scala-steward/pull/527) by [@fthomas](https://github.com/fthomas)
- Log reason why an update is ignored [#523](https://github.com/fthomas/scala-steward/pull/523) by [@fthomas](https://github.com/fthomas)
- Add a semver "label" to PRs [#492](https://github.com/fthomas/scala-steward/pull/492) by [@fthomas](https://github.com/fthomas)
- Introduce Gitlab support [#524](https://github.com/fthomas/scala-steward/pull/524) by [@daddykotex](https://github.com/daddykotex)
- Add ability to propose sbt version update [#665](https://github.com/fthomas/scala-steward/pull/665) by [@exoego](https://github.com/exoego)
- Bitbucket support [#647](https://github.com/fthomas/scala-steward/pull/647) by [@dpfeiffer](https://github.com/dpfeiffer)
- Ability to set log level via env variables [#624](https://github.com/fthomas/scala-steward/pull/624) by [@anilkumarmyla](https://github.com/anilkumarmyla)
- Have a fantastic day writing Scala! [#583](https://github.com/fthomas/scala-steward/pull/583) by [@kubukoz](https://github.com/kubukoz)
- Ignore another plantuml version [#601](https://github.com/fthomas/scala-steward/pull/601) by [@fthomas](https://github.com/fthomas)
- Ignore dependencies introduced by plugins [#584](https://github.com/fthomas/scala-steward/pull/584) by [@fthomas](https://github.com/fthomas)
- scalafix integration [#153](https://github.com/fthomas/scala-steward/pull/153) by [@fthomas](https://github.com/fthomas)
- Ignore 'scala' as search term in the 'sliding' heuristic [#560](https://github.com/fthomas/scala-steward/pull/560) by [@fthomas](https://github.com/fthomas)

#### Bug Fixes

- Ignore strings that contain the version as proper substring [#565](https://github.com/fthomas/scala-steward/pull/565) by [@fthomas](https://github.com/fthomas)
- Ignore updates where currentVersion and newerVersion are identical [#559](https://github.com/fthomas/scala-steward/pull/559) by [@fthomas](https://github.com/fthomas)
- Include repo in head param when searching for PRs [#552](https://github.com/fthomas/scala-steward/pull/552) by [@fthomas](https://github.com/fthomas)
- Refine replaceAllInGroupId [#547](https://github.com/fthomas/scala-steward/pull/547) by [@fthomas](https://github.com/fthomas)
- Do not ignore artifactId in replaceAllInImpl [#531](https://github.com/fthomas/scala-steward/pull/531) by [@fthomas](https://github.com/fthomas)
- Remove InvalidVersions from Update.newerVersions [#494](https://github.com/fthomas/scala-steward/pull/494) by [@fthomas](https://github.com/fthomas)
- Ignore SNAPSHOT updates if current version is not already a SNAPSHOT [#485](https://github.com/fthomas/scala-steward/pull/485) by [@fthomas](https://github.com/fthomas)
- Elevate pre release semver [#648](https://github.com/fthomas/scala-steward/pull/648) by [@ChristopherDavenport](https://github.com/ChristopherDavenport)
- Call .value outside of lambda [#588](https://github.com/fthomas/scala-steward/pull/588) by [@fthomas](https://github.com/fthomas)
- Install sbt-scalafix plugin only during migrations [#602](https://github.com/fthomas/scala-steward/pull/602) by [@fthomas](https://github.com/fthomas)
- Ignore untracked files when checking if a repository has changes [#571](https://github.com/fthomas/scala-steward/pull/571) by [@fthomas](https://github.com/fthomas)
- Fix exception "named capturing group is missing trailing '}'" [#566](https://github.com/fthomas/scala-steward/pull/566) by [@fthomas](https://github.com/fthomas)

#### Documentation

- Update run documentation [#683](https://github.com/fthomas/scala-steward/pull/683) by [@cchantep](https://github.com/cchantep)
- Add link to badge and use brightgreen [#667](https://github.com/fthomas/scala-steward/pull/667) by [@fthomas](https://github.com/fthomas)
- Add Scala Steward badge [#666](https://github.com/fthomas/scala-steward/pull/666) by [@exoego](https://github.com/exoego)
- Update running.md with --vcs-api-host --vcs-login flags [#660](https://github.com/fthomas/scala-steward/pull/660) by [@custommonkey](https://github.com/custommonkey)
- Add link to GH search for example Mergify rules [#651](https://github.com/fthomas/scala-steward/pull/651) by [@fthomas](https://github.com/fthomas)
- Link to list of PRs that add Scalafix migrations [#634](https://github.com/fthomas/scala-steward/pull/634) by [@fthomas](https://github.com/fthomas)
- FAQ: How does Scala Steward decide what version it is updating to? [#633](https://github.com/fthomas/scala-steward/pull/633) by [@fthomas](https://github.com/fthomas)
- Add FAQs [#632](https://github.com/fthomas/scala-steward/pull/632) by [@fthomas](https://github.com/fthomas)
- Move "Running scala-steward" section to docs/ [#610](https://github.com/fthomas/scala-steward/pull/610) by [@fthomas](https://github.com/fthomas)

#### Test Improvements

- Test FileAlg.removeTemporarily with a nonexistent file [#594](https://github.com/fthomas/scala-steward/pull/594) by [@fthomas](https://github.com/fthomas)
- Remove MockState.extraEnv [#597](https://github.com/fthomas/scala-steward/pull/597) by [@fthomas](https://github.com/fthomas)
- Fix Cogen[Version] [#586](https://github.com/fthomas/scala-steward/pull/586) by [@fthomas](https://github.com/fthomas)
- Test OrderLaws for Version [#578](https://github.com/fthomas/scala-steward/pull/578) by [@fthomas](https://github.com/fthomas)

#### Build Improvements

- Try using OpenJDKs on Travis CI [#627](https://github.com/fthomas/scala-steward/pull/627) by [@fthomas](https://github.com/fthomas)

#### Dependency Updates

- Update http4s-blaze-client, http4s-circe, ... to 0.20.3 [#567](https://github.com/fthomas/scala-steward/pull/567) by [@scala-steward](https://github.com/scala-steward)
- Update http4s-blaze-client, http4s-circe, ... to 0.20.2 [#564](https://github.com/fthomas/scala-steward/pull/564) by [@scala-steward](https://github.com/scala-steward)
- Update fs2-core to 1.0.5 [#554](https://github.com/fthomas/scala-steward/pull/554) by [@scala-steward](https://github.com/scala-steward)
- Update refined, refined-cats, ... to 0.9.8 [#555](https://github.com/fthomas/scala-steward/pull/555) by [@scala-steward](https://github.com/scala-steward)
- Update scalatest to 3.0.8 [#556](https://github.com/fthomas/scala-steward/pull/556) by [@scala-steward](https://github.com/scala-steward)
- Update case-app to 2.0.0-M9 [#551](https://github.com/fthomas/scala-steward/pull/551) by [@scala-steward](https://github.com/scala-steward)
- Update kind-projector to 0.10.3 [#550](https://github.com/fthomas/scala-steward/pull/550) by [@scala-steward](https://github.com/scala-steward)
- Update wartremover to 2.4.2 [#549](https://github.com/fthomas/scala-steward/pull/549) by [@scala-steward](https://github.com/scala-steward)
- Update refined, refined-cats, ... to 0.9.7 [#537](https://github.com/fthomas/scala-steward/pull/537) by [@scala-steward](https://github.com/scala-steward)
- Update kind-projector to 0.10.2 [#534](https://github.com/fthomas/scala-steward/pull/534) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-native-packager to 1.3.22 [#530](https://github.com/fthomas/scala-steward/pull/530) by [@scala-steward](https://github.com/scala-steward)
- Update cats-effect to 1.3.1 [#520](https://github.com/fthomas/scala-steward/pull/520) by [@scala-steward](https://github.com/scala-steward)
- Update refined, refined-cats, ... to 0.9.6 [#519](https://github.com/fthomas/scala-steward/pull/519) by [@scala-steward](https://github.com/scala-steward)
- Update kind-projector to 0.10.1 [#499](https://github.com/fthomas/scala-steward/pull/499) by [@scala-steward](https://github.com/scala-steward)
- Update better-files to 3.8.0 [#477](https://github.com/fthomas/scala-steward/pull/477) by [@scala-steward](https://github.com/scala-steward)
- Update http4s-blaze-client, http4s-circe, ... to 0.20.6 [#682](https://github.com/fthomas/scala-steward/pull/682) by [@scala-steward](https://github.com/scala-steward)
- Update http4s-blaze-client, http4s-circe, ... to 0.20.4 [#669](https://github.com/fthomas/scala-steward/pull/669) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-scalafmt to 2.0.2 [#635](https://github.com/fthomas/scala-steward/pull/635) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-assembly to 0.14.10 [#664](https://github.com/fthomas/scala-steward/pull/664) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-scalajs-crossproject to 0.6.1 [#631](https://github.com/fthomas/scala-steward/pull/631) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-native-packager to 1.3.24 [#630](https://github.com/fthomas/scala-steward/pull/630) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-native-packager to 1.3.23 [#619](https://github.com/fthomas/scala-steward/pull/619) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-tpolecat to 0.1.7 [#608](https://github.com/fthomas/scala-steward/pull/608) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-updates to 0.4.1 [#600](https://github.com/fthomas/scala-steward/pull/600) by [@fthomas](https://github.com/fthomas)
- Update sbt-scalafmt to 2.0.1 [#558](https://github.com/fthomas/scala-steward/pull/558) by [@fthomas](https://github.com/fthomas)

#### Refactorings

- Move code for replacing versions from Update to UpdateHeuristic [#557](https://github.com/fthomas/scala-steward/pull/557) by [@fthomas](https://github.com/fthomas)
- Several refactorings [#548](https://github.com/fthomas/scala-steward/pull/548) by [@fthomas](https://github.com/fthomas)
- Remove unneeded Sync context bound [#543](https://github.com/fthomas/scala-steward/pull/543) by [@fthomas](https://github.com/fthomas)
- Do not call replaceAllInImpl for each source file [#528](https://github.com/fthomas/scala-steward/pull/528) by [@fthomas](https://github.com/fthomas)
- Read RepoConfig only once when updating a repo [#522](https://github.com/fthomas/scala-steward/pull/522) by [@fthomas](https://github.com/fthomas)
- Refactor to move github/data to vcs/data [#497](https://github.com/fthomas/scala-steward/pull/497) by [@daddykotex](https://github.com/daddykotex)
- Remove cross-version suffix from artifactIds [#493](https://github.com/fthomas/scala-steward/pull/493) by [@fthomas](https://github.com/fthomas)
- Add SbtAlg.getSbtVersion [#673](https://github.com/fthomas/scala-steward/pull/673) by [@fthomas](https://github.com/fthomas)
- Split getRepoConfig into readRepoConfig{,OrDefault} [#650](https://github.com/fthomas/scala-steward/pull/650) by [@fthomas](https://github.com/fthomas)
- Simplify bindUntilTrue, evalFilter [#644](https://github.com/fthomas/scala-steward/pull/644) by [@kubukoz](https://github.com/kubukoz)
- Optimize JsonUpdateRepository [#640](https://github.com/fthomas/scala-steward/pull/640) by [@fthomas](https://github.com/fthomas)
- Move parseDependencies to sbt.parser [#638](https://github.com/fthomas/scala-steward/pull/638) by [@fthomas](https://github.com/fthomas)
- Tiny code tweaks [#587](https://github.com/fthomas/scala-steward/pull/587) by [@jhnsmth](https://github.com/jhnsmth)
- Log count of repos (total, filtered, pruned) [#590](https://github.com/fthomas/scala-steward/pull/590) by [@fthomas](https://github.com/fthomas)
- Use BigInt instead of Long for the numeric parts of Version [#579](https://github.com/fthomas/scala-steward/pull/579) by [@fthomas](https://github.com/fthomas)
- Put all global sbt plugins in scala-steward.sbt [#581](https://github.com/fthomas/scala-steward/pull/581) by [@fthomas](https://github.com/fthomas)
- Extract padToSameLength from Order[Version] [#580](https://github.com/fthomas/scala-steward/pull/580) by [@fthomas](https://github.com/fthomas)
- Rewrite steward as StewardAlg and remove Context class [#575](https://github.com/fthomas/scala-steward/pull/575) by [@fthomas](https://github.com/fthomas)
- Make Cli a class [#572](https://github.com/fthomas/scala-steward/pull/572) by [@fthomas](https://github.com/fthomas)
- SemVer.parse: filter inside the for comprehension [#570](https://github.com/fthomas/scala-steward/pull/570) by [@fthomas](https://github.com/fthomas)
- Use better-monadic-for [#561](https://github.com/fthomas/scala-steward/pull/561) by [@fthomas](https://github.com/fthomas)
