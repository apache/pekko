# Agent Rules for Apache Pekko

Follow `CONTRIBUTING.md`. If this file conflicts with `CONTRIBUTING.md`, follow `CONTRIBUTING.md` and update this file.

## Worktree Rules

- Base new work on `origin/main` unless the user or maintainer requests another branch.
- Keep every PR scoped to one change.
- Do not mix behavior changes, formatting churn, dependency updates, and unrelated cleanup.
- Do not revert user changes or unrelated local changes.
- Use `rg` or `rg --files` for repository searches.
- Read neighboring code before editing.
- Preserve existing license and copyright notices.
- Do not add `@author` tags.

## PR Rules

- Every non-doc-only PR must add or update a directional test.
- Directional test: focused behavior or regression test with explicit assertions.
- Bug-fix tests must fail or expose the issue before the fix.
- CI-flake tests must encode the intended ordering, scheduling, timeout, or concurrency contract.
- Documentation-only PRs must use `Tests: Not run - docs only`.
- Behavior, configuration, public API, or operator changes must update docs.
- Scala and Java DSL changes must keep API, docs, and tests in parity.
- Public API, binary shape, serialization, or MiMa-sensitive internal changes must preserve binary compatibility.
- The GitHub `Check / Binary Compatibility` job must pass before merge.
- Wire protocol changes must consider rolling upgrade compatibility.
- Dependency changes must verify Apache-compatible licenses.

## Formatting Rules

- Prefer native scalafmt for changed Scala and SBT files when it is available.

```shell
git fetch origin main
scalafmt --mode diff-ref=origin/main
scalafmt --list --mode diff-ref=origin/main
```

- If native scalafmt is not installed, use the sbt scalafmt tasks or record that scalafmt could not be run.

```shell
sbt scalafmtAll scalafmtSbt
sbt scalafmtCheckAll scalafmtSbtCheck
```

- Use JDK 17 for Java formatter tasks.

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
sbt javafmtAll
```

- Run header generation before PR.

```shell
sbt headerCreateAll
```

- Run checks when relevant.

```shell
sbt javafmtCheckAll
sbt +headerCheckAll
sbt checkCodeStyle
```

- Use `sbt sortImports` when imports change.
- Do not rely on IDE formatting alone.
- Do not commit unrelated formatting changes.

## Validation Rules

- Run the smallest focused test first.

```shell
sbt "module-name / Test / testOnly fully.qualified.SpecName"
```

- Use JDK-specific configs when relevant.

```shell
sbt "module-name / TestJdk9 / testOnly fully.qualified.SpecName"
```

- Run PR impact validation for non-trivial code changes.

```shell
sbt validatePullRequest
```

- Set `PR_TARGET_BRANCH` only when the PR target is not `main`.

```shell
PR_TARGET_BRANCH=origin/example sbt validatePullRequest
```

- Run MiMa for public API, binary shape, serialization, or MiMa-sensitive internal changes.

```shell
sbt +mimaReportBinaryIssues
```

- Do not mark a PR ready while the local MiMa run or the GitHub `Check / Binary Compatibility` job is failing.
- Run Paradox for documentation changes that touch project docs.

```shell
sbt docs/paradox
```

- Always run `git diff --check`.
- Do not assume local tools such as `sbt` or `scalafmt` are installed; if a required tool is missing, record the missing tool and skipped command in `Tests`.
- Skipped or environment-failed commands must be recorded in `Tests`.

## Test Rules

- Prefer deterministic tests over larger timeouts.
- Avoid `Thread.sleep`.
- Prefer probes, latches, stepped components, `within`, `remaining`, and `remainingOrDefault`.
- Keep `expectNoMessage` short and intentional.
- Do not weaken assertions to hide production behavior.
- Do not fix flakes only by increasing timeouts.

## Code Rules

- Match existing module patterns.
- Prefer local helpers and established abstractions.
- Put non-public shared implementation in `internal` packages where appropriate.
- Mark Java-visible internals with `private[pekko]`, `@InternalApi`, and `INTERNAL API` Scaladoc.
- For public Java APIs, avoid Scala default parameters, lower type bounds, deeply nested traits, and Scala-only collection types.
- Use `scala.jdk.*` converters for Java/Scala interop.
- For new Pekko Streams operators, update operator docs and consistency coverage.

## CI and JDK Rules

- Read exact GitHub Actions logs before changing code for CI failures.
- Reproduce JDK-specific failures on the same JDK when possible.
- For JDK 21+ nightly virtual-thread behavior, read the matching section in `CONTRIBUTING.md`.
- Do not treat a pass on a different JDK as proof for a JDK-specific failure.

## Commit and PR Format

- Use this body format for non-trivial commits.

```text
Motivation:
Problem or requirement.

Modification:
Change summary.

Result:
New outcome.

Tests:
- command/result or Not run - docs only

References:
Fixes #1234, Refs #1234, or None - <short context>
```

- Use this PR body format.

```markdown
### Motivation
Problem or requirement.

### Modification
Change summary.

### Result
New outcome.

### Tests
- command/result or Not run - docs only

### References
Fixes #1234, Refs #1234, or None - <short context>
```

- Never omit `Tests`.
- Never omit `References`.
- Use `Refs #...`, `Fixes #...`, or `None - <short context>`.
