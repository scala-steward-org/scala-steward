<img src="https://github.com/fthomas/scala-steward/raw/master/data/images/scala-steward-logo-hex-1.png" width="156px" height="180px" align="right">

# scala-steward
[![Build Status](https://travis-ci.org/fthomas/scala-steward.svg?branch=master)](https://travis-ci.org/fthomas/scala-steward)
[![codecov](https://codecov.io/gh/fthomas/scala-steward/branch/master/graph/badge.svg)](https://codecov.io/gh/fthomas/scala-steward)
[![Join the chat at https://gitter.im/fthomas/scala-steward](https://badges.gitter.im/fthomas/scala-steward.svg)](https://gitter.im/fthomas/scala-steward?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4573461025c642daa4128b659ee54fc9)](https://www.codacy.com/app/fthomas/scala-steward?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=fthomas/scala-steward&amp;utm_campaign=Badge_Grade)
[![Typelevel project](https://img.shields.io/badge/typelevel-project-brightgreen.svg)](https://typelevel.org/projects/#scala-steward)

scala-steward is a robot that helps you keeping library dependencies
and sbt plugins up-to-date.

## Quick start guide

Open a pull request that adds the GitHub repository of your Scala project
to [repos.md](https://github.com/fthomas/scala-steward/blob/master/repos.md)
([edit](https://github.com/fthomas/scala-steward/edit/master/repos.md)).
Once that PR is merged, [**@scala-steward**][@scala-steward] will check
periodically for updates of libraries and plugins in your project and will
open pull requests for updates it found.

## Show us the pull requests!

If you are curious how [**@scala-steward**'s][@scala-steward] pull requests
look like, here are the ones it has created so far:

* [Created pull requests](https://github.com/search?q=author%3Ascala-steward+is%3Apr)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Apr))
* [Merged pull requests]( https://github.com/search?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc))

## Contributors

Thanks goes to these wonderful people:

* [Arulselvan Madhavan](https://github.com/ArulselvanMadhavan)
* [Bayram Kiran](https://github.com/kiranbayram)
* [Dale Wijnand](https://github.com/dwijnand)
* [David Francoeur](https://github.com/daddykotex)
* [Filipe Regadas](https://github.com/regadas)
* [Frank S. Thomas](https://github.com/fthomas)
* [JCollier](https://github.com/Slakah)
* [Jeff Martin](https://github.com/custommonkey)
* [kenji yoshida](https://github.com/xuwei-k)
* [Mark Canlas](https://github.com/mcanlas)
* [Michael Wizner](https://github.com/mwz)
* [Philippus Baalman](https://github.com/Philippus)
* [Piotr Gabara](https://github.com/pgabara)
* [Renato Cavalcanti](https://github.com/renatocaval)
* [sullis](https://github.com/sullis)
* [Thomas Kaliakos](https://github.com/thomaska)
* [Zelenya](https://github.com/Zelenya)

## Community

The following companies are using scala-steward to manage their dependencies.
Using scala-steward in your company and don't see it listed here?
Consider creating PR to add your company to the list and join the community.

* [Chartboost](https://www.chartboost.com/)
* [HolidayCheck](https://github.com/holidaycheck)
* [iAdvize](https://www.iadvize.com/en/)
* [SlamData](https://slamdata.com/)
* [Snowplow Analytics](https://snowplowanalytics.com/)
* [SoftwareMill](https://softwaremill.com)
* [Zalando](https://en.zalando.de/)

## Participation

The scala-steward project supports the [Scala Code of Conduct][CoC]
and wants all of its channels (GitHub, Gitter, etc.) to be welcoming
environments for everyone.

## Credit

scala-steward wouldn't exist without the great [sbt-updates][sbt-updates]
plugin to determine dependency updates and a bunch of [Typelevel][Typelevel]
and other Scala [libraries](https://github.com/fthomas/scala-steward/blob/master/project/Dependencies.scala).

[**@scala-steward**][@scala-steward]'s cute profile picture is by
[@impurepics](https://twitter.com/impurepics/).

## Running scala-steward

```bash
sbt stage

./modules/core/.jvm/target/universal/stage/bin/scala-steward \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$STEWARD_DIR/repos.md" \
  --git-author-name "Scala steward" \
  --git-author-email ${EMAIL} \
  --github-api-host "https://api.github.com" \
  --github-login ${LOGIN} \
  --git-ask-pass "$STEWARD_DIR/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR
```

Or,

```bash
sbt docker:publishLocal

docker run -v $STEWARD_DIR:/opt/scala-steward -it scala-steward:0.1.0-SNAPSHOT \
  --workspace  "/opt/scala-steward/workspace" \
  --repos-file "/opt/scala-steward/repos.md" \
  --git-author-name "Scala steward" \
  --git-author-email ${EMAIL} \
  --github-api-host "https://api.github.com" \
  --github-login ${LOGIN} \
  --git-ask-pass "/opt/scala-steward/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR
```

If you run scala-steward for your own private projects, you can pass additional environment variables from the command line using the `--env-var` flag as shown in the examples above. You can use this to pass any credentials required by your projects to resolve any private dependencies, e.g.: 

```bash
--env-var BINTRAY_USER=username \
--env-var BINTRAY_PASS=password
```

These variables will be accessible (in sbt) to all of the projects that scala-steward checks dependencies for.

## License

scala-steward is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

[CoC]: https://github.com/fthomas/scala-steward/blob/master/CODE_OF_CONDUCT.md
[@scala-steward]: https://github.com/scala-steward
[sbt-updates]: https://github.com/rtimush/sbt-updates
[Typelevel]: https://typelevel.org/
