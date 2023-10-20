<p align="center">
  <img src="https://github.com/scala-steward-org/scala-steward/raw/main/data/images/scala-steward-logo-circle-0.png" height="180px">
</p>

# Scala Steward
[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/scala-steward-org/scala-steward/ci.yml?branch=main)](https://github.com/scala-steward-org/scala-steward/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/scala-steward-org/scala-steward/branch/main/graph/badge.svg)](https://codecov.io/gh/scala-steward-org/scala-steward)
[![Join the chat at https://gitter.im/scala-steward-org/scala-steward](https://badges.gitter.im/scala-steward-org/scala-steward.svg)](https://gitter.im/scala-steward-org/scala-steward?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Typelevel project](https://img.shields.io/badge/typelevel-project-blue.svg)](https://typelevel.org/projects/#scala-steward)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Docker Pulls](https://img.shields.io/docker/pulls/fthomas/scala-steward.svg?style=flat&color=blue)](https://hub.docker.com/r/fthomas/scala-steward/)

Scala Steward is a bot that helps you keep your library dependencies, sbt plugins, and Scala and sbt versions up-to-date.

See also the announcement blog post:
[*Keep your projects up-to-date with Scala Steward*](https://www.scala-lang.org/blog/2019/07/10/announcing-scala-steward.html)

## Quick start guide

Open a pull request that adds the GitHub repository of your project to [repos-github.md](https://github.com/VirtusLab/scala-steward-repos/blob/main/repos-github.md) ([edit](https://github.com/VirtusLab/scala-steward-repos/edit/main/repos-github.md)).
Once that PR is merged, [**@scala-steward**][@scala-steward] will check periodically for version updates in your project and will open pull requests for updates it found.

Many thanks to [VirtusLab][VirtusLab] for hosting and managing this public Scala Steward instance!

## Show us the pull requests!

If you are curious how [**@scala-steward**'s][@scala-steward] pull requests
look like, here are the ones it has created so far:

* [Created pull requests](https://github.com/search?q=author%3Ascala-steward+is%3Apr)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Apr))
* [Merged pull requests]( https://github.com/search?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc&type=pullrequests)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc))

You can also watch what it is currently doing [here](https://gitstalk.netlify.com/scala-steward).

## Wanna have a badge?

A badge is available to show that Scala Steward is helping your repos.
Let's spread Scala Steward to keep Scala ecosystem updated.

[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

```markdown
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
```

## Documentation

The [`docs`](docs) directory contains documentation about these topics:

* [Running Scala Steward](docs/running.md)
* [Scalafix Migrations](docs/scalafix-migrations.md)
* [Frequently Asked Questions](docs/faq.md)
* [Repository-specific configuration](docs/repo-specific-configuration.md)
* [Artifact Migrations](docs/artifact-migrations.md)

## Contributors

Thanks goes to these wonderful people for contributing to Scala Steward:

* [Alejandro Hernández](https://github.com/alejandrohdezma)
* [Alessandro Buggin](https://github.com/abuggin)
* [Alex](https://github.com/jhnsmth)
* [Alex Klibisz](https://github.com/alexklibisz)
* [Alexis Hernandez](https://github.com/AlexITC)
* [Andrea](https://github.com/Andrea)
* [Andrea Mistretta](https://github.com/andreami)
* [Anil Kumar Myla](https://github.com/anilkumarmyla)
* [Anton Sviridov](https://github.com/keynmol)
* [Antonio Gelameris](https://github.com/TonioGela)
* [Arjun Dhawan](https://github.com/arjun-1)
* [Arman Bilge](https://github.com/armanbilge)
* [Arnold Farkas](https://github.com/sapka12)
* [Arulselvan Madhavan](https://github.com/ArulselvanMadhavan)
* [Barry O'Neill](https://github.com/barryoneill)
* [Bayram Kiran](https://github.com/kiranbayram)
* [Ben Carter](https://github.com/bcarter97)
* [Brice Jaglin](https://github.com/bjaglin)
* [Cédric Chantepie](https://github.com/cchantep)
* [Chris Kipp](https://github.com/ckipp01)
* [Chris Llanwarne](https://github.com/cjllanwarne)
* [Christoph Meier](https://github.com/meier-christoph)
* [Christopher Davenport](https://github.com/ChristopherDavenport)
* [Claudio Bley](https://github.com/avdv)
* [cwholmes](https://github.com/cwholmes)
* [Dale Wijnand](https://github.com/dwijnand)
* [Daniel Esik](https://github.com/danicheg)
* [Daniel Pfeiffer](https://github.com/dpfeiffer)
* [Daniel Spiewak](https://github.com/djspiewak)
* [Daniil Leontiev](https://github.com/danielleontiev)
* [David Francoeur](https://github.com/daddykotex)
* [Declan](https://github.com/d-g-n)
* [Dominic Egger](https://github.com/GrafBlutwurst)
* [Don Smith III](https://github.com/cactauz)
* [Doug Roper](https://github.com/htmldoug)
* [Eldar Yusupov](https://github.com/eyusupov)
* [Ender Tunç](https://github.com/endertunc)
* [Erik Erlandson](https://github.com/erikerlandson)
* [Erlend Hamnaberg](https://github.com/hamnis)
* [eugeniyk](https://github.com/eugeniyk)
* [Fabian Grutsch](https://github.com/fgrutsch)
* [Felix Dietze](https://github.com/fdietze)
* [Filipe Regadas](https://github.com/regadas)
* [Florian Meriaux](https://github.com/fmeriaux)
* [Frank S. Thomas](https://github.com/fthomas)
* [Frederick Roth](https://github.com/froth)
* [Georgy Davityan](https://github.com/implmnt)
* [Grzegorz Kocur](https://github.com/gkocur)
* [Harm Weites](https://github.com/harmw)
* [Ikenna Darlington Ogbajie](https://github.com/idarlington)
* [Ingar Abrahamsen](https://github.com/ingarabr)
* [Jakub Kozłowski](https://github.com/kubukoz)
* [Javier Arrieta](https://github.com/javierarrieta)
* [JCollier](https://github.com/Slakah)
* [Jeff Martin](https://github.com/custommonkey)
* [Jichao Ouyang](https://github.com/jcouyang)
* [Joan Goyeau](https://github.com/joan38)
* [José Eduardo Montenegro Cavalcanti de Oliveira](https://github.com/edumco)
* [kalejami](https://github.com/kalejami)
* [KAWACHI Takashi](https://github.com/tkawachi)
* [kenji yoshida](https://github.com/xuwei-k)
* [Kilic Ali-Firat](https://github.com/alifirat)
* [LaurenceWarne](https://github.com/LaurenceWarne)
* [Leonhard Riedißer](https://github.com/L7R7)
* [Maksym Ochenashko](https://github.com/iRevive)
* [Manuel Cueto](https://github.com/manuelcueto)
* [Marco Zühlke](https://github.com/mzuehlke) 
* [Mark Canlas](https://github.com/mcanlas)
* [Mark van der Tol](https://github.com/markvandertol)
* [MaT1g3R](https://github.com/MaT1g3R)
* [Mat Mannion](https://github.com/matmannion)
* [Matthias Kurz](https://github.com/mkurz)
* [Maxence Cramet](https://github.com/max5599)
* [Michael Wizner](https://github.com/mwz)
* [Michel Daviot](https://github.com/tyrcho)
* [miguelpuyol](https://github.com/miguelpuyol)
* [nafg](https://github.com/nafg)
* [Nabil Abdel-Hafeez](https://github.com/987Nabil)
* [Ondra Pelech](https://github.com/sideeffffect)
* [Pavel Shapkin](https://github.com/psttf)
* [Philippus Baalman](https://github.com/Philippus)
* [Piotr Gabara](https://github.com/pgabara)
* [PJ Fanning](https://github.com/pjfanning)
* [Renato Cavalcanti](https://github.com/renatocaval)
* [Rikito Taniguchi](https://github.com/tanishiking)
* [Robert Stoll](https://github.com/robstoll)
* [Roberto Tyley](https://github.com/rtyley)
* [Robin Raju](https://github.com/robinraju)
* [Roman Langolf](https://github.com/rolang)
* [Ropiteaux Théo](https://github.com/ropiteaux)
* [Scott Rice](https://github.com/scottrice10)
* [Seeta Ramayya](https://github.com/Seetaramayya)
* [solar](https://github.com/solar)
* [Stanislav Chetvertkov](https://github.com/stanislav-chetvertkov)
* [sullis](https://github.com/sullis)
* [TATSUNO Yasuhiro](https://github.com/exoego)
* [Terry Hendrix](https://github.com/terryhendrix1990)
* [Thomas Heslin](https://github.com/tjheslin1)
* [Thomas Kaliakos](https://github.com/thomaska)
* [Tim Steinbach](https://github.com/NeQuissimus)
* [Tobias Roeser](https://github.com/lefou)
* [Toshiyuki Takahashi](https://github.com/tototoshi)
* [Victor Viale](https://github.com/Koroeskohr)
* [Yan](https://github.com/yaroot)
* [Yannick Heiber](https://github.com/ybasket)
* [Yoan Alvarez-Vanhard](https://github.com/tyoras)
* [Zack Powers](https://github.com/Milyardo)
* [Zelenya](https://github.com/Zelenya)

## Community

The following companies are using Scala Steward to manage their dependencies.
Using Scala Steward in your company and don't see it listed here?
Consider creating PR to add your company to the list and join the community.

* [Adform](https://site.adform.com/)
* [Agoda](https://agoda.com/)
* [AutoScout24](https://www.autoscout24.de/)
* [Avast](https://avast.com)
* [Babylon Health](https://www.babylonhealth.com/)
* [Besedo](https://www.besedo.com/)
* [Bitrock](http://www.bitrock.it/)
* [Chartboost](https://www.chartboost.com/)
* [Colisweb](https://www.colisweb.com/)
* [commercetools](https://docs.commercetools.com/)
* [Dataswift.io](https://dataswift.io/)
* [Enliven Systems](https://enliven.systems)
* [Evolution Gaming](https://www.evolutiongaming.com/)
* [Firstbird](https://firstbird.com)
* [Hellosoda](https://hellosoda.com/)
* [HolidayCheck](https://github.com/holidaycheck)
* [iAdvize](https://www.iadvize.com/en/)
* [LeadIQ](https://leadiq.com/)
* [Lightbend](https://www.lightbend.com/)
* [Mobimeo](https://www.mobimeo.com/)
* [Ocado Technology](https://ocadotechnology.com/)
* [Play Framework](https://www.playframework.com/)
* [PlayQ](https://www.playq.com/)
* [Precog](https://precog.com/)
* [Rewards Network](https://www.rewardsnetwork.com/)
* [Rivero](https://rivero.tech/)
* [Septeni Original](https://www.septeni-original.co.jp)
* [Snowplow Analytics](https://snowplowanalytics.com/)
* [SoftwareMill](https://softwaremill.com)
* [Spotify](https://www.spotify.com)
* [SpringerNature](https://www.springernature.com)
* [Teads](https://medium.com/teads-engineering)
* [Tegonal GmbH](https://tegonal.com)
* [Tupl](https://www.tupl.com)
* [VirtusLab](https://virtuslab.com/)
* [wehkamp](https://www.wehkamp.nl/)
* [Wiringbits](https://wiringbits.net)
* [Zalando](https://en.zalando.de/)

## Participation

The Scala Steward project supports the [Scala Code of Conduct][CoC]
and wants all of its channels (GitHub, Gitter, etc.) to be welcoming
environments for everyone.

## Credit

Scala Steward wouldn't exist without the great [sbt-updates][sbt-updates]
plugin which was used until version 0.6 to find dependency updates.

Thanks goes also to [**@impurepics**](https://twitter.com/impurepics)
for [**@scala-steward**][@scala-steward]'s cute profile picture and to
the maintainers and contributors of the various
[libraries](https://github.com/scala-steward-org/scala-steward/blob/master/project/Dependencies.scala)
this project depends on.

## License

Scala Steward is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

[CoC]: https://github.com/scala-steward-org/scala-steward/blob/master/CODE_OF_CONDUCT.md
[@scala-steward]: https://github.com/scala-steward
[sbt-updates]: https://github.com/rtimush/sbt-updates
[VirtusLab]: https://www.virtuslab.com
