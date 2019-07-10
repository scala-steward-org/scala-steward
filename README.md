<img src="https://github.com/fthomas/scala-steward/raw/master/data/images/scala-steward-logo-hex-1.png" width="156px" height="180px" align="right">

# Scala Steward
[![Build Status](https://travis-ci.org/fthomas/scala-steward.svg?branch=master)](https://travis-ci.org/fthomas/scala-steward)
[![codecov](https://codecov.io/gh/fthomas/scala-steward/branch/master/graph/badge.svg)](https://codecov.io/gh/fthomas/scala-steward)
[![Join the chat at https://gitter.im/fthomas/scala-steward](https://badges.gitter.im/fthomas/scala-steward.svg)](https://gitter.im/fthomas/scala-steward?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4573461025c642daa4128b659ee54fc9)](https://www.codacy.com/app/fthomas/scala-steward?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=fthomas/scala-steward&amp;utm_campaign=Badge_Grade)
[![Typelevel project](https://img.shields.io/badge/typelevel-project-brightgreen.svg)](https://typelevel.org/projects/#scala-steward)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-brightgreen.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Scala Steward is a robot that helps you keeping library dependencies
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

## Wanna have a badge ?

A badge is available to show that Scala Steward is helping your repos.
Let's spread Scala Steward to keep Scala ecosystem updated.

[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-brightgreen.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

```markdown
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-brightgreen.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
```

## Documentation

The [`docs`](docs) directory contains documentation about these topics:

* [Running Scala Steward](docs/running.md)
* [Scalafix Migrations](docs/scalafix-migrations.md)
* [Frequently Asked Questions](docs/faq.md)

## Contributors

Thanks goes to these wonderful people:

* [Alex](https://github.com/jhnsmth)
* [Arulselvan Madhavan](https://github.com/ArulselvanMadhavan)
* [Bayram Kiran](https://github.com/kiranbayram)
* [Cédric Chantepie](https://github.com/cchantep)
* [Christopher Davenport](https://github.com/ChristopherDavenport)
* [Dale Wijnand](https://github.com/dwijnand)
* [Daniel Pfeiffer](https://github.com/dpfeiffer)
* [David Francoeur](https://github.com/daddykotex)
* [Fabian](https://github.com/fg-devs)
* [Filipe Regadas](https://github.com/regadas)
* [Frank S. Thomas](https://github.com/fthomas)
* [Jakub Kozłowski](https://github.com/kubukoz)
* [JCollier](https://github.com/Slakah)
* [Jeff Martin](https://github.com/custommonkey)
* [kenji yoshida](https://github.com/xuwei-k)
* [Mark Canlas](https://github.com/mcanlas)
* [Michael Wizner](https://github.com/mwz)
* [Philippus Baalman](https://github.com/Philippus)
* [Piotr Gabara](https://github.com/pgabara)
* [Renato Cavalcanti](https://github.com/renatocaval)
* [sullis](https://github.com/sullis)
* [TATSUNO Yasuhiro](https://github.com/exoego)
* [Thomas Heslin](https://github.com/tjheslin1)
* [Thomas Kaliakos](https://github.com/thomaska)
* [Zelenya](https://github.com/Zelenya)

## Community

The following companies are using Scala Steward to manage their dependencies.
Using Scala Steward in your company and don't see it listed here?
Consider creating PR to add your company to the list and join the community.

* [Chartboost](https://www.chartboost.com/)
* [Firstbird](https://firstbird.com)
* [HolidayCheck](https://github.com/holidaycheck)
* [iAdvize](https://www.iadvize.com/en/)
* [SlamData](https://slamdata.com/)
* [Snowplow Analytics](https://snowplowanalytics.com/)
* [SpringerNature](https://www.springernature.com)
* [SoftwareMill](https://softwaremill.com)
* [Zalando](https://en.zalando.de/)

## Participation

The Scala Steward project supports the [Scala Code of Conduct][CoC]
and wants all of its channels (GitHub, Gitter, etc.) to be welcoming
environments for everyone.

## Credit

Scala Steward wouldn't exist without the great [sbt-updates][sbt-updates]
plugin to determine dependency updates and a bunch of [Typelevel][Typelevel]
and other Scala [libraries](https://github.com/fthomas/scala-steward/blob/master/project/Dependencies.scala).

[**@scala-steward**][@scala-steward]'s cute profile picture is by
[@impurepics](https://twitter.com/impurepics/).

## License

Scala Steward is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

[CoC]: https://github.com/fthomas/scala-steward/blob/master/CODE_OF_CONDUCT.md
[@scala-steward]: https://github.com/scala-steward
[sbt-updates]: https://github.com/rtimush/sbt-updates
[Typelevel]: https://typelevel.org/
