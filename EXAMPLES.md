## Good examples

Below you can find dependency examples which will be identified by scala-steward without any problems:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.24")
val scalajsJqueryVersion = "0.9.3"
val SCALAJSJQUERYVERSION = "0.9.3"
val scalajsjquery = "0.9.3"
"be.doeraene" %% "scalajs-jquery"  % "0.9.3"
```

More examples can be checked at [UpdateTest](https://github.com/fthomas/scala-steward/blob/master/modules/core/src/test/scala/org/scalasteward/core/model/UpdateTest.scala).

## Bad examples

Here are the examples which scala-steward won't be able to identify:

```scala
// multi-line dependency definition
val scalajsJqueryVersion =
  "0.9.3"

// version name with backticks
val `scala-js-jquery-version` = "0.9.3"
```
