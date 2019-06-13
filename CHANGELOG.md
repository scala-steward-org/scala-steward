# Changelog

## v0.2.0 (09/05/2019)

#### Enhancements

- Always use Config.gitHubLogin in PR body [#359](https://github.com/fthomas/scala-steward/pull/359) by [@fthomas](https://github.com/fthomas)
- Add details section to PR about ignoring updates [#304](https://github.com/fthomas/scala-steward/pull/304) by [@fthomas](https://github.com/fthomas)
- Ignore updates via a repo-specific .scala-steward.conf file [#301](https://github.com/fthomas/scala-steward/pull/301) by [@fthomas](https://github.com/fthomas)
- replaceAllInRelaxed: split camel case artifactIds [#466](https://github.com/fthomas/scala-steward/pull/466) by [@fthomas](https://github.com/fthomas)
- Use --no-gpg-sign option if signCommits is false [#423](https://github.com/fthomas/scala-steward/pull/423) by [@fthomas](https://github.com/fthomas)
- Include base branch when searching for existing PRs [#427](https://github.com/fthomas/scala-steward/pull/427) by [@fthomas](https://github.com/fthomas)
- #365 Add --ignore-opts-files option [#380](https://github.com/fthomas/scala-steward/pull/380) by [@Slakah](https://github.com/Slakah)
- Adds configuration settings to disable PR updates [#388](https://github.com/fthomas/scala-steward/pull/388) by [@renatocaval](https://github.com/renatocaval)
- Ability to inject environment variables to firejail [#337](https://github.com/fthomas/scala-steward/pull/337) by [@mwz](https://github.com/mwz)
- Log filename if FileAlg.editFile fails [#400](https://github.com/fthomas/scala-steward/pull/400) by [@fthomas](https://github.com/fthomas)
- Persist state of PRs [#396](https://github.com/fthomas/scala-steward/pull/396) by [@fthomas](https://github.com/fthomas)
- Log parsed RepoConfig from .scala-steward.conf files [#391](https://github.com/fthomas/scala-steward/pull/391) by [@fthomas](https://github.com/fthomas)

#### Bug Fixes

- Ignore lines that contain "mimaPreviousArtifacts" [#300](https://github.com/fthomas/scala-steward/pull/300) by [@fthomas](https://github.com/fthomas)
- Extend ignore comments tests and fix a tiny bug [#431](https://github.com/fthomas/scala-steward/pull/431) by [@kiranbayram](https://github.com/kiranbayram)
- Ignore comment lines [#426](https://github.com/fthomas/scala-steward/pull/426) by [@kiranbayram](https://github.com/kiranbayram)

#### Test Improvements

- Add cwd parameter in MockProcessAlg.exec to MockState [#360](https://github.com/fthomas/scala-steward/pull/360) by [@fthomas](https://github.com/fthomas)
- Test GitHubApiAlg via Http4sGitHubApiAlg [#342](https://github.com/fthomas/scala-steward/pull/342) by [@fthomas](https://github.com/fthomas)
- Test FileAlg.removeTemporarily [#326](https://github.com/fthomas/scala-steward/pull/326) by [@fthomas](https://github.com/fthomas)
- Test FileAlg.{deleteForce, readFile, writeFile} [#303](https://github.com/fthomas/scala-steward/pull/303) by [@fthomas](https://github.com/fthomas)
- Test replaceAllIn with backticks around the search term [#458](https://github.com/fthomas/scala-steward/pull/458) by [@fthomas](https://github.com/fthomas)
- Fix tests to be platform-agnostic [#387](https://github.com/fthomas/scala-steward/pull/387) by [@dwijnand](https://github.com/dwijnand)

#### Build Improvements

- Run docker:publishLocal to validate Docker image [#317](https://github.com/fthomas/scala-steward/pull/317) by [@fthomas](https://github.com/fthomas)
- Set DEBIAN_FRONTEND to noninteractive in docker build [#312](https://github.com/fthomas/scala-steward/pull/312) by [@mwz](https://github.com/mwz)
- Try replacing openjdk11 with oraclejdk11 [#428](https://github.com/fthomas/scala-steward/pull/428) by [@fthomas](https://github.com/fthomas)
- Remove unnecessary readme project [#394](https://github.com/fthomas/scala-steward/pull/394) by [@fthomas](https://github.com/fthomas)

#### Dependency Updates

- Update sbt-native-packager to 1.3.20 [#372](https://github.com/fthomas/scala-steward/pull/372) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-travisci to 1.2.0 [#366](https://github.com/fthomas/scala-steward/pull/366) by [@scala-steward](https://github.com/scala-steward)
- Update http4s-blaze-client, http4s-circe, ... to 0.20.0-M7 [#355](https://github.com/fthomas/scala-steward/pull/355) by [@scala-steward](https://github.com/scala-steward)
- Update scalatest to 3.0.7 [#352](https://github.com/fthomas/scala-steward/pull/352) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-header to 5.2.0 [#341](https://github.com/fthomas/scala-steward/pull/341) by [@scala-steward](https://github.com/scala-steward)
- Update monocle-core to 1.5.1-cats [#335](https://github.com/fthomas/scala-steward/pull/335) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-native-packager to 1.3.19 [#332](https://github.com/fthomas/scala-steward/pull/332) by [@scala-steward](https://github.com/scala-steward)
- Update better-files to 3.7.1 [#331](https://github.com/fthomas/scala-steward/pull/331) by [@scala-steward](https://github.com/scala-steward)
- Update fs2-core to 1.0.4 [#329](https://github.com/fthomas/scala-steward/pull/329) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-explicit-dependencies to 0.2.9 [#320](https://github.com/fthomas/scala-steward/pull/320) by [@scala-steward](https://github.com/scala-steward)
- Update case-app to 2.0.0-M6 [#310](https://github.com/fthomas/scala-steward/pull/310) by [@scala-steward](https://github.com/scala-steward)
- Update http4s-blaze-client, http4s-circe to 0.20.0-M6 [#305](https://github.com/fthomas/scala-steward/pull/305) by [@scala-steward](https://github.com/scala-steward)
- Update log4cats-slf4j to 0.3.0 [#302](https://github.com/fthomas/scala-steward/pull/302) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-native-packager to 1.3.18 [#298](https://github.com/fthomas/scala-steward/pull/298) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-native-packager to 1.3.21 [#468](https://github.com/fthomas/scala-steward/pull/468) by [@scala-steward](https://github.com/scala-steward)
- Update cats-effect to 1.3.0 [#459](https://github.com/fthomas/scala-steward/pull/459) by [@scala-steward](https://github.com/scala-steward)
- Update scalatest to 3.0.6 [#330](https://github.com/fthomas/scala-steward/pull/330) by [@custommonkey](https://github.com/custommonkey)
- Update http4s-blaze-client, http4s-circe, ... to 0.20.0 [#419](https://github.com/fthomas/scala-steward/pull/419) by [@scala-steward](https://github.com/scala-steward)
- Update refined, refined-scalacheck to 0.9.5 [#410](https://github.com/fthomas/scala-steward/pull/410) by [@scala-steward](https://github.com/scala-steward)
- Update case-app to 2.0.0-M7 [#405](https://github.com/fthomas/scala-steward/pull/405) by [@scala-steward](https://github.com/scala-steward)
- Update kind-projector to 0.10.0 [#399](https://github.com/fthomas/scala-steward/pull/399) by [@fthomas](https://github.com/fthomas)
- Update kind-projector to 0.9.10 [#392](https://github.com/fthomas/scala-steward/pull/392) by [@scala-steward](https://github.com/scala-steward)
- Update http4s-blaze-client, http4s-circe, ... to 0.20.0-RC1 [#386](https://github.com/fthomas/scala-steward/pull/386) by [@scala-steward](https://github.com/scala-steward)
- Update sbt-tpolecat to 0.1.6 [#385](https://github.com/fthomas/scala-steward/pull/385) by [@scala-steward](https://github.com/scala-steward)

#### Refactorings

- Pass repo to the function for modifying requests [#373](https://github.com/fthomas/scala-steward/pull/373) by [@fthomas](https://github.com/fthomas)
- Add algebra for cloning and syncing GitHub repos [#361](https://github.com/fthomas/scala-steward/pull/361) by [@fthomas](https://github.com/fthomas)
- Extract HttpJsonClient from Http4sGitHubApiAlg [#356](https://github.com/fthomas/scala-steward/pull/356) by [@fthomas](https://github.com/fthomas)
- Decouple authorization from Http4sGitHubApiAlg [#354](https://github.com/fthomas/scala-steward/pull/354) by [@fthomas](https://github.com/fthomas)
- Parse githubApiHost as Uri [#340](https://github.com/fthomas/scala-steward/pull/340) by [@fthomas](https://github.com/fthomas)
- Use Uri instead of String for GitHub URLs [#338](https://github.com/fthomas/scala-steward/pull/338) by [@fthomas](https://github.com/fthomas)
- Implement withUserInfo as Optional[Uri, UserInfo] [#334](https://github.com/fthomas/scala-steward/pull/334) by [@fthomas](https://github.com/fthomas)
- Move constraints on F[_] to the class level [#328](https://github.com/fthomas/scala-steward/pull/328) by [@fthomas](https://github.com/fthomas)
-  Replace LoggerOps with LogAlg  [#325](https://github.com/fthomas/scala-steward/pull/325) by [@fthomas](https://github.com/fthomas)
- Implement attemptLog_ directly without attemptLog [#321](https://github.com/fthomas/scala-steward/pull/321) by [@fthomas](https://github.com/fthomas)
- Do not repeat commands in getUpdatesForRepo [#297](https://github.com/fthomas/scala-steward/pull/297) by [@fthomas](https://github.com/fthomas)
- Extract function from Update.replaceAllInImpl [#476](https://github.com/fthomas/scala-steward/pull/476) by [@fthomas](https://github.com/fthomas)
- Remove distinct from splitBetweenLowerAndUpperChars [#467](https://github.com/fthomas/scala-steward/pull/467) by [@fthomas](https://github.com/fthomas)
- Use a dedicated type for the state of a PR [#395](https://github.com/fthomas/scala-steward/pull/395) by [@fthomas](https://github.com/fthomas)
