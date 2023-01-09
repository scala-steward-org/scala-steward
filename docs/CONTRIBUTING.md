
## Reference

### Helpful resources about writing and structuring documentation

* [What nobody tells you about documentation](https://www.divio.com/blog/documentation/)

## How-to

### Debug a case of "Unable to bump version"

1. Write a representative test in `RewriteTest.scala`.
2. Optionally, print the logs by adding `state.trace.foreach(println)` in the `runApplyUpdate` method.
3. Optionally, create a test that intentionally fails by using the `test("...".fail) { ... }` construct.
4. Optionally, open a PR to get input from the maintainers and community.
