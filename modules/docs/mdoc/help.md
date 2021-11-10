# CLI help

All command line arguments for the `scala-steward` application.

```scala mdoc:passthrough
import caseapp.core.help._
import org.scalasteward.core.application.Cli._
println(Help[Args].help(HelpFormat.default(false)))
```
