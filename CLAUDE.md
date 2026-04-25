# Claude Rules for Apache Pekko

Follow `AGENTS.md`.

Before opening or updating a PR, verify:

- Non-doc-only changes have directional tests.
- Native `scalafmt` or the sbt scalafmt tasks were run for changed Scala/SBT files, or the missing tool is recorded in `Tests`.
- `sbt javafmtAll` was run with JDK 17 when relevant.
- `sbt headerCreateAll` was run.
- Binary compatibility is preserved, and the GitHub `Check / Binary Compatibility` job passes before merge.
- `sbt +mimaReportBinaryIssues` was run for public API, binary shape, serialization, or MiMa-sensitive internal changes.
- Commit messages follow the `AGENTS.md` format.
- PR bodies follow the `AGENTS.md` format.
- `Tests` and `References` are present.
