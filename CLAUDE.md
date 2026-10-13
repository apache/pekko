# Claude Rules for Apache Pekko

Follow `AGENTS.md`.

Before opening or updating a PR, verify:

- Non-doc-only changes have directional tests.
- Native `scalafmt` or the sbt scalafmt tasks were run for changed Scala/SBT files, or the missing tool is recorded in `Tests`.
- Code must use syntax compatible with both Scala 2.13 and Scala 3. Do not use Scala 3-only syntax such as significant indentation, `as` for import rename, `*` for wildcard import, or postfix `*` for vararg splices. The `.scalafmt.conf` enforces these restrictions via `dialectOverride`.
- `sbt javafmtAll` was run with JDK 17 when relevant.
- `sbt headerCreateAll` was run to add headers for new files. Never hand-write or invent license headers; let sbt manage them, and preserve existing copyright notices intact.
- For copied code, the source file or external project is noted in the PR (see Licensing Rules in `AGENTS.md`).
- Binary compatibility is preserved, and the GitHub `Check / Binary Compatibility` job passes before merge.
- `sbt +mimaReportBinaryIssues` was run for public API, binary shape, serialization, or MiMa-sensitive internal changes, and ALL reported issues were fixed before creating or updating the PR.
- Commit messages follow the `AGENTS.md` format.
- PR bodies follow the `AGENTS.md` format.
- `Tests` and `References` are present.
- No `Co-authored-by` or AI-assistant trailers are added to commits or PR descriptions.
