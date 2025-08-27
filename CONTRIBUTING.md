# Contributing to Apache Pekko

We follow the standard GitHub [fork & pull](https://help.github.com/articles/using-pull-requests/#fork--pull) approach to pull requests. Just fork the official repo, develop in a branch, and submit a PR!

You're always welcome to submit your PR straight away and start the discussion (without reading the rest of this wonderful doc or the README.md). The goal of these notes is to make your experience contributing to Pekko as smooth and pleasant as possible. We're happy to guide you through the process once you've submitted your PR.

## The Apache Pekko Community

If you have questions about the contribution process or discuss specific issues, please interact with the community using the community links in the [README](README.md).

## Navigating around the project & codebase

### Branches summary

Depending on which version (or sometimes module) you want to work on, you should target a specific branch as explained below:

* `main` – active development branch of Pekko
* `1.0.x` – maintenance branch of Pekko 1.0
* `1.1.x` – maintenance branch of Pekko 1.1
* `1.2.x` – maintenance branch of Pekko 1.2

### Tags

Pekko uses tags to categorise issues into groups or mark their phase in development.

In general *all issues are open for anyone working on them*. However, if you're new to the project and looking for an issue
that will be accepted and likely is a nice one to get started you should check out the following tags:

- [good first issue](https://github.com/apache/pekko/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22) - which identifies simple entry-level tickets, such as improvements of documentation or tests. If you're not sure how to solve a ticket but would like to work on it, feel free to ask in the issue about clarification or tips.
- [help wanted](https://github.com/apache/pekko/labels/help%20wanted) - identifies issues that the core team will likely not have time to work on or that are nice entry-level tickets. If you're not sure how to solve a ticket but would like to work on it, feel free to ask in the issue about clarification or tips.
- [nice-to-have (low-priority)](https://github.com/apache/pekko/labels/nice-to-have%20%28low-prio%29) - are tasks which make sense but are not a very high priority (in the face of other very high priority issues). If you see something interesting in this list, a contribution would be really wonderful!

If you see an issue that you would like to work on, comment on the issue so that others will know that someone is having a look.

These special tags indicates specific states a ticket is in:

- [bug](https://github.com/apache/pekko/labels/bug) indicates potential production issues. Bugs take priority in being fixed above features. The core team dedicates some days to work on bugs in each sprint. Bugs which have reproducers are also great for community contributions as they're well-isolated. Sometimes we're not as lucky to have reproducers, though, then a bugfix should also include a test reproducing the original error along with the fix.
- [failed](https://github.com/apache/pekko/labels/failed) indicates a CI failure (for example, from a nightly build). These tickets usually include a stacktrace + link to the failed job, and we'll add a comment when we see the same problem again. Since these tickets can either indicate tests with incorrect assumptions, or legitimate issues in the production code, we look at them periodically. When the same problem isn't seen again over a period of 6 months we assume it to be a rare flaky test or a problem that might have since been fixed, so we close the issue until it pops up again.

## Pekko contributing guidelines

These guidelines apply to all Apache Pekko projects, by which we currently mean both the `apache/pekko` repository, as well as any plugins or additional repositories.

These guidelines are meant to be a living document that should be changed and adapted as needed.
We encourage changes that make it easier to achieve our goals efficiently.

### General workflow

The steps below describe how to get a patch into the main development branch (`main`).
The steps are exactly the same for everyone involved in the project, including the core team and first-time contributors.

1. To avoid duplicated effort, it might be good to check the [issue tracker](https://github.com/apache/pekko/issues) and [existing pull requests](https://github.com/apache/pekko/pulls) for existing work.
   - If there is no ticket yet, feel free to [create one](https://github.com/apache/pekko/issues/new/choose) to discuss the problem and the approach you want to take to solve it.
1. [Fork the project](https://github.com/apache/pekko/fork) on GitHub. You'll need to create a feature-branch for your work on your fork, as this way you'll be able to submit a pull request against the mainline Pekko.
1. Create a branch on your fork and work on the feature. For example: `git checkout -b custom-headers-pekko-http`
   - Please make sure to follow the general quality guidelines (specified below) when developing your patch.
   - Please write additional tests covering your feature and adjust existing ones if needed before submitting your pull request. The `validatePullRequest` sbt task ([explained below](#the-validatepullrequest-task)) may come in handy to verify your changes are correct.
   - Use the `checkCodeStyle` sbt task to ensure your code is properly formatted and includes the proper copyright headers.
1. Once your feature is complete, prepare the commit following our guide [Creating Commits And Writing Commit Messages](#creating-commits-and-writing-commit-messages). For example, a good commit message would be: `Adding compression support for Manifests #22222` (note the reference to the ticket it aimed to resolve).
1. If it's a new feature or a change of behavior, document it on the [docs](https://github.com/apache/pekko/tree/main/docs). When the feature touches Scala and Java DSL, document both the Scala and Java APIs.
1. Now it's finally time to [submit the pull request](https://help.github.com/articles/using-pull-requests)!
   - Please make sure to include a reference to the issue you're solving *in the comment* for the Pull Request, as this will cause the PR to be linked properly with the issue. Examples of good phrases for this are: "Resolves #1234" or "Refs #1234".
1. If you are a first time contributor, a core member must approve the CI to run for your pull request.
1. For non-trivial changes, you will be asked to sign the [CLA](https://www.apache.org/licenses/contributor-agreements.html) if you have not done so before.
1. Now, both committers and interested people will review your code. This process ensures that the code we merge is of the best possible quality and that no silly mistakes slip through. You're expected to follow-up on these comments by adding new commits to the same branch. The commit messages of those commits can be more loose, for example: `Removed debugging using printline`, as they all will be squashed into one commit before merging into the main branch.
   - The community and core team are really nice people, so don't be afraid to ask follow-up questions if you didn't understand some comment or would like clarification on how to continue with a given feature. We're here to help, so feel free to ask and discuss any questions you might have during the review process!
1. After the review, you should fix the issues as needed (pushing a new commit for a new review, etc.), iterating until the reviewers give their approval signaled by GitHub's pull-request approval feature. Usually, a reviewer will add an `LGTM` comment, which means "Looks Good To Me".
   - In general, a PR is expected to get 1 approval from the team before it is merged. Larger or more complicated PRs would ideally have more people look at it before it gets merged.
1. If the code change needs to be applied to other branches as well (for example, a bugfix needing to be backported to a previous version), one of the team members will either ask you to submit a PR with the same commits to the old branch or will do this for you.
   - Follow the [backporting steps](#backporting) below.
1. Once everything is said and done, your pull request gets merged :tada:! Your feature will be available with the next "earliest" release milestone (i.e. if backported so that it will be in release x.y.z, find the relevant milestone for that release). Of course, you will be given credit for the fix in the release stats during the release's announcement. You've made it!

The TL;DR; of the above very precise workflow version is:

1. Fork Pekko
2. Hack and test on your feature (on a branch)
3. Document it
4. Submit a PR
5. Sign the [CLA](https://www.apache.org/licenses/contributor-agreements.html) if necessary
6. Keep polishing it until getting the required number of approvals
7. Profit!

> **Note:** Github Actions runs all the workflows for the forked project. We have filters to ensure that each action effectively runs only for the `apache/pekko` repository, but you may also want to [disable Github Actions](https://docs.github.com/en/github/administering-a-repository/managing-repository-settings/disabling-or-limiting-github-actions-for-a-repository) entirely in your fork.

#### Backporting

If you feel that a PR should be backported so that it will appear in a patch release, comment on the PR. The Pekko team will decide if the backport is a good idea and may request that the original contributor cherry pick the commit into a new PR.

### Getting started with sbt

Pekko is using the [sbt](https://github.com/sbt/sbt) build system. So the first thing you have to do is to download and install sbt. You can read more about how to do that in the [sbt setup](https://www.scala-sbt.org/1.x/docs/Getting-Started.html) documentation.

To compile all the Pekko core modules, use the `compile` command:

```shell
sbt compile
```

You can run all tests with the `test` command:

```shell
sbt test
```

If you want to deploy the artifacts to your local Ivy repository (for example,
to use from an sbt project) use the `publishLocal` command:

```shell
sbt publishLocal
```

Note that in the examples above we are calling `sbt compile` and `sbt test`
and so on, but sbt also has an interactive mode. If you just run `sbt`, you
start the interactive sbt shell and enter the commands directly. This saves
starting up a new JVM instance for each command and can be much faster and more
convenient.

For example, building Pekko as above is more commonly done like this:

```
% sbt
[info] Set current project to default (in build file:/.../pekko/project/plugins/)
[info] Set current project to pekko (in build file:/.../pekko/)
> compile
...
> test
...
```

To run a single multi-jvm test:

```shell
sbt
project cluster
MultiJvm/testOnly org.apache.pekko.cluster.SunnyWeather
```

To format the Scala source code:

```shell
sbt
cluster/scalafmtAll
persistence/scalafmtAll
```

To format the Java source code:

```shell
sbt
project actor
javafmtAll
```

To keep the *import*s sorted with:

```shell
sbt
project actor
sortImports
```

To verify code style with:

```shell
sbt
checkCodeStyle
```

To apply code style with:

```shell
sbt
applyCodeStyle
```

To format Scala code more faster, you could format code with [Scala-CLI](https://scala-cli.virtuslab.org/) or [Coursier CLI](https://scalameta.org/scalafmt/docs/installation.html#cli):

**With Scala-Cli**
```shell
scala-cli fmt
```
**With Coursier CLI**
```Shell
cs install scalafmt // skip it if scalafmt is already installed. If you are a macOS or Linux user, you can simply download the native binaries from the Coursier CLI installation page.
scalafmt
```

#### Do not use `-optimize` Scala compiler flag

Pekko has not been compiled or tested with `-optimize` Scala compiler flag. (In sbt, you can specify compiler options in the `scalacOptions` key.)
Strange behavior has been reported by users that have tried it.

#### Compiling with Graal JIT

Pekko, like most Scala projects, compiles faster with the Graal JIT enabled. The easiest way to use it for compiling Pekko is to:

* Use a JDK > 10
* Use the following JVM options for SBT e.g. by adding them to the `SBT_OPTS` environment variable: `-XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:+UseJVMCICompiler`

### The `validatePullRequest` task

The Pekko build includes a special task called `validatePullRequest`, which investigates the changes made as well as dirty
(uncommitted changes) in your local working directory and figures out which projects are impacted by those changes,
then running tests only on those projects.

For example, changing something in `actor` would cause tests to be run in all projects which depend on it
(e.g. `actor-tests`, `stream`, `docs` etc.).

To use the task, simply type `validatePullRequest`, and the output should include entries like shown below:

```shell
> validatePullRequest
[info] Diffing [HEAD] to determine changed modules in PR...
[info] Detected uncomitted changes in directories (including in dependency analysis): [protobuf,project]
[info] Detected changes in directories: [actor-tests, project, stream, docs, persistence]
```

By default, changes are diffed with the `main` branch when working locally. If you want to validate against a different
target PR branch, you can do so by setting the PR_TARGET_BRANCH environment variable for sbt:

```shell
PR_TARGET_BRANCH=origin/example sbt validatePullRequest
```

If you already ran all tests and just need to check formatting and MiMa, there
is a set of `all*` command aliases that run `Test/compile` (also formats), `mimaReportBinaryIssues`, and `validateCompile`
(compiles `multi-jvm` if enabled for that project). See `build.sbt` or use completion to find the most appropriate one
e.g. `allCluster`, `allTyped`.

### Binary compatibility

Binary compatibility rules and guarantees are described in depth in the [Binary Compatibility Rules
](https://pekko.apache.org/) section of the documentation.

Pekko uses [MiMa](https://github.com/lightbend/mima) to validate the binary compatibility of incoming pull requests. If your
PR fails due to binary compatibility issues, you may see an error like this:

```
[info] stream: found 1 potential binary incompatibilities while checking against org.apache.pekko:pekko-stream_2.12:2.4.2  (filtered 222)
[error]  * method foldAsync(java.lang.Object,scala.Function2)org.apache.pekko.stream.scaladsl.FlowOps in trait org.apache.pekko.stream.scaladsl.FlowOps is present only in current version
[error]    filter with: ProblemFilters.exclude[ReversedMissingMethodProblem]("org.apache.pekko.stream.scaladsl.FlowOps.foldAsync")
```

In such situations it's good to consult with a core team member whether the violation can be safely ignored or if it would indeed
break binary compatibility. If the violation can be ignored add exclude statements from the mima output to
a new file named `<module>/src/main/mima-filters/<last-version>.backwards.excludes/<pr-or-issue>-<issue-number>-<description>.excludes`,
e.g. `actor/src/main/mima-filters/2.6.0.backwards.excludes/pr-12345-rename-internal-classes.excludes`. Make sure to add a comment
in the file that describes briefly why the incompatibility can be ignored.

Situations when it may be acceptable to ignore a MiMa issued warning include:

- if it is touching any class marked as `private[pekko]`, `/** INTERNAL API*/` or similar markers
- if it is concerning internal classes (often recognisable by package names like `dungeon`, `impl`, `internal` etc.)
- if it is adding API to classes / traits which are only meant for extension by Pekko itself, i.e. should not be extended by end-users
- other tricky situations

The binary compatibility of the current changes can be checked by running `sbt +mimaReportBinaryIssues`.

### Wire compatibility

Changes to the binary protocol of remoting, cluster and the cluster tools require great care so that it is possible to do rolling upgrades. Note that this may include older nodes communicating with a newer node, so compatibility may have to be both ways.

Since during a rolling upgrade nodes producing the 'new' format and nodes producing the 'old' format coexist, a change can require a two-release process: the first change is to add a new binary format but still use the old one. A second step then starts actually emitting the new wire format. This ensures users can complete a rolling upgrade first to the intermediate version and then another
rolling upgrade to the next version.

All wire protocol changes that may concern rolling upgrades should be documented in the
[Rolling Update Changelog](https://pekko.apache.org/)
(found in docs/src/main/paradox/project/rolling-update.md)

### Protobuf

Pekko includes a shaded version of protobuf `3` that is used for internal communication. To generate files,
run `protobufGenerate`. The generated files are put in each project's `src/main/java` and need to be committed.
The generated files are automatically transformed to use the shaded version of protobuf and add copyright headers.

Generation depends on the protoc version specified in `project/Dependencies.scala` being on the path. See [protobuf project](https://github.com/protocolbuffers/protobuf#protocol-compiler-installation) for installation instructions, and
[Protobuf.scala](https://github.com/apache/pekko/blob/main/project/Protobuf.scala) for details of how to override
the settings for generation.

After generation, run `sbt javafmt Test/javafmt`.

### Pull request requirements

For a pull request to be considered at all, it has to meet these requirements:

1. Regardless if the code introduces new features or fixes bugs or regressions, it must have comprehensive tests.
1. The code must be well documented as per the existing documentation format (see the 'Documentation' section below).
1. The commit messages must properly describe the changes. See further below.
1. A pull request must be [linked to the issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue) it aims to resolve in the PR's description (or comments). This can be achieved by writing "Fixes #1234" or similar in PR description.
1. Licensing rules:
   - Existing files with copyright statements must leave those copyright statements intact
   - New files should have an Apache license header instead. For an example of this, see [this file](https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/SparkContext.scala).

#### Additional guidelines

Some additional guidelines regarding source code are:

- Keep the code [DRY](https://en.wikipedia.org/wiki/Don%27t_repeat_yourself).
- Apply the [Boy Scout Rule](https://www.oreilly.com/library/view/97-things-every/9780596809515/ch08.html) whenever you have the chance to.
- Never delete or change existing copyright notices, just add additional info.
- Do not use "@author "tags since it does not encourage [Collective Code Ownership](http://www.extremeprogramming.org/rules/collective.html).
  Contributors, each project should ensure that the contributors get the credit they deserve—in a text file or page on the project website and in the release notes, etc.

### Documentation

All documentation is preferred to be in Lightbend's standard documentation format [Paradox](https://github.com/lightbend/paradox), which among other things, allows all code in the documentation to be externalized into compiled files and imported into the documentation.

To build the documentation locally:

```shell
sbt
docs/paradox
```

The generated HTML documentation is in `docs/target/paradox/site/main/index.html`.

Alternatively, use `docs/paradoxBrowse` to open the generated docs in your default web browser.

#### Links to API documentation

Pekko Paradox supports directives to link to the Scaladoc- and Javadoc-generated API documentation:

* `@apidoc[Flow]` searches for the class name and creates links to Scaladoc and Javadoc (see variants in [sbt-paradox-apidoc](https://github.com/lightbend/sbt-paradox-apidoc#examples))
* `@scaladoc[Flow](org.apache.pekko.stream.scaladsl.Flow)` (see [Paradox docs](https://developer.lightbend.com/docs/paradox/current/directives/linking.html#scaladoc-directive))
* `@javadoc[Flow](org.apache.pekko.stream.javadsl.Flow)` (see [Paradox docs](https://developer.lightbend.com/docs/paradox/current/directives/linking.html#javadoc-directive))

#### Scaladoc

Pekko generates class diagrams for the API documentation using ScalaDoc.

Links to methods in ScalaDoc comments should be formatted
`[[Like#this]]`, because `[[this]]` does not work with [genjavadoc](https://github.com/typesafehub/genjavadoc), and
IntelliJ warns about `[[#this]]`.
For further hints on how to disambiguate links in ScalaDoc comments see
[this StackOverflow answer](https://stackoverflow.com/a/31569861/354132),
though note that this syntax may not correctly render as Javadoc.

The Scaladoc tool needs the `dot` command from the [Graphviz](https://graphviz.org/#download) software package to be installed to avoid errors. You can disable the diagram generation by adding the flag `-Dpekko.scaladoc.diagrams=false`. After installing Graphviz, make sure you add the toolset to the `PATH` (definitely on Windows).

#### JavaDoc

Pekko generates JavaDoc-style API documentation using the [genjavadoc](https://github.com/typesafehub/genjavadoc) sbt plugin, since the sources are written mostly in Scala.

Generating JavaDoc is not enabled by default, as it's not needed on day-to-day development as it's expected to just work.
If you'd like to check if your links and formatting look good in JavaDoc (and not only in ScalaDoc), you can generate it by running:

```shell
sbt -Dpekko.genjavadoc.enabled=true Javaunidoc/doc
```

Which will generate JavaDoc style docs in `./target/javaunidoc/index.html`. This requires a JDK version 11 or later.

#### Changing the project information page index

In case you want to change the index of `Project Information` page, you need to change it on the
`project/ProjectIndexGenerator.scala` so it can be included in the automatic docs generation.

#### License Report Generation

By default, license report generation is disabled to improve compilation speed. You can enable it by configuring `-Dpekko.genlicensereport.enabled=true`. e.g.:

- `sbt -Dpekko.genlicensereport.enabled=true "docs/paradox"`
- `sbt -Dpekko.genlicensereport.enabled=true shell` And then `docs/paradox`

### External dependencies

All the external runtime dependencies for the project, including transitive dependencies, must have an open source license that is equal to, or compatible with, [Apache 2](https://www.apache.org/licenses/LICENSE-2.0).

This must be ensured by manually verifying the license for all the dependencies for the project:

1. Whenever a committer to the project changes a version of a dependency (including Scala) in the build file.
2. Whenever a committer to the project adds a new dependency.
3. Whenever a new release is cut (public or private for a customer).

Which licenses are compatible with Apache 2 are defined in [this doc](https://www.apache.org/legal/resolved.html#category-a), where you can see that the licenses that are listed under "Category A "are automatically compatible with Apache 2, while the ones listed under ["Category B "](https://www.apache.org/legal/resolved.html#category-b) need additional action:

> Each license in this section requires some degree of reciprocity. This may require additional action to minimize the chance that a user of an Apache product will create a derivative work of a differently-licensed portion of an Apache product without being aware of the applicable requirements.

Each project must also create and maintain a list of all dependencies and their licenses, including all their transitive dependencies. This can be done either in the documentation or in the build file next to each dependency.

### Creating commits and writing commit messages

Follow these guidelines when creating public commits and writing commit messages.

1. The first line should be a descriptive sentence what the commit is doing, including the ticket number. It should be possible to fully understand what the commit does—but not necessarily how it does it—by just reading this single line. We follow the "imperative present tense" style for commit messages ([more info here](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)).

   It is **not ok** to only list the ticket number, type "minor fix" or similar.
   If the commit is a small fix, then you are done. If not, go to 2.

2. Following the single line description should be a blank line followed by an enumerated list with the details of the commit.

3. You can request a review by a specific team member  for your commit (depending on the degree of automation we reach, the list may change over time):
   * "Review by @gituser "- if you want to notify someone on the team. The others can and are encouraged to participate.

Example:

```
Enable Travis CI #1

* Details 1
* Details 2
* Details 3
```

### Ignoring formatting commits in git blame

Throughout the history of the codebase various formatting commits have been applied as the scalafmt style has evolved over time, if desired
one can setup git blame to ignore these commits. The hashes for these specific are stored in [this file](.git-blame-ignore-revs) so to configure
git blame to ignore these commits you can execute the following.

```shell
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

### Pull request validation workflow details

Pekko uses GitHub Actions to validate pull requests, which involves checking code style, running tests, checking binary compatibility, etc.

For existing contributors, Github Actions will run without requiring any manual intervention from a core team member.

For first time contributors, the workflow will be run after an approval from a core team member. After that, whenever new commits are pushed to the pull request, a validation job will be automatically started.

To speed up PR validation times the Pekko build contains a special sbt task called `validatePullRequest`,
which is smart enough to figure out which projects should be built if a PR only has changes in some parts of the project.
For example, if your PR only touches `persistence`, no `remote` tests need to be run, however the task
will validate all projects that depend on `persistence` (including samples).
Also, tests tagged as `PerformanceTest`, `TimingTest`, `LongRunningTest`, and all multi-node tests are excluded from PR validation.

You can exclude the same kind of tests in your local build by starting sbt with:

```shell
sbt -Dpekko.test.tags.exclude=performance,timing,long-running -Dpekko.test.multi-in-test=false
```

It is also possible to exclude groups of test by their names. For example:

```shell
sbt -Dpekko.test.names.exclude=org.apache.pekko.cluster.Stress
```

Will exclude any tests that have names containing `org.apache.pekko.cluster.Stress`.

### Source style

Sometimes it is convenient to place 'internal' classes in their own package.
In such situations, we prefer 'internal' over 'impl' as a package name.

#### Scala style

Pekko uses [Scalafmt](https://scalameta.org/scalafmt/docs/installation.html) to enforce some of the code style rules.

It's recommended to enable Scalafmt formatting in IntelliJ. Use version 2019.1 or later. In
Preferences > Editor > Code Style > Scala, select Scalafmt as formatter and enable "Reformat on file save".
IntelliJ will then use the same settings and version as defined in `.scalafmt.conf` file. Then it's
not needed to use `sbt scalafmtAll` when editing with IntelliJ.

PR validation includes checking that the Scala sources are formatted and will fail if they are not.

Pekko prefers flattened imports rather than grouped, which helps reduce merge conflicts.
If you are using IntelliJ IDEA, you can disable it by unchecking: `Preferences` -> `Code Style` -> `Scala` -> `Imports` -> `Merge imports with the same prefix into one statement`.


It's recommended to run `sbt +sortImports` to keep the *import*s sorted.

#### Java style

Pekko uses [the sbt Java Formatter plugin](https://github.com/sbt/sbt-java-formatter) to format Java sources.

PR validation includes checking that the Java sources are formatted and will fail if they are not.

#### Code discipline opt out

In addition to formatting, the Pekko build enforces code discipline through a set of compiler flags. While exploring ideas, the discipline may be more of a hindrance than a help. Therefore, it is possible to disable it by setting the system property `pekko.no.discipline`
to any non-empty string value when starting up sbt:

```shell
sbt -Dpekko.no.discipline=youbet
```

PR validation includes the discipline flags and hence may fail if the flags were disabled during development. Make sure you compile your code at least once with discipline enabled before sending a PR.

#### Preferred ways to use timeouts in tests

Avoid short test timeouts since Github Actions runners may GC heavily, causing spurious test failures. GC pause or other hiccups of 2 seconds are common in our CI environment. Please note that usually giving a larger timeout *does not slow down the tests*, as in an `expectMessage` call for example it usually will complete quickly.

There are a number of ways timeouts can be defined in Pekko tests. The following ways to use timeouts are recommended (in order of preference):

* `remaining` is the first choice (requires `within` block)
* `remainingOrDefault` is the second choice
* `3.seconds` is the third choice if not using testkit
* lower timeouts must come with a very good reason (e.g. Awaiting on a known to be "already completed" `Future`)

Special care should be given to `expectNoMessage` calls, which indeed will wait for the entire timeout before continuing. Therefore a shorter timeout should be used in those, for example `200.millis` or `300.millis`. Prefer the method without timeout parameter, which will use the configured `expect-no-message-default` timeout.

You can read up on `remaining` and friends in [TestKit.scala](https://github.com/apache/pekko/blob/main/testkit/src/main/scala/org/apache/pekko/testkit/TestKit.scala).

### Contributing modules

For external contributions of entire features, the normal way is to establish it as a stand-alone project first, to show that there is a need for the feature. If there is enough interested, the
next step would be to add it to Pekko as an "may change"-feature (possibly in a new subproject) and marking it's public api with the `ApiMayChange` annotation, then when the feature is hardened, well documented and tested it becomes an officially supported Pekko feature.

[List of Pekko features marked as may change](https://pekko.apache.org/docs/pekko/current/common/may-change.html)

### Java APIs in Pekko

Pekko aims to keep 100% feature parity between Java and Scala. Implementing even the API for Java in
Scala has proven the most viable way to do it, as long as you keep the following in mind:

1. Keep entry points separated in `javadsl` and `scaladsl` unless changing existing APIs which for historical
   and binary compatibility reasons do not have this subdivision.

1. Have methods in the `javadsl` package delegate to the methods in the Scala API, or the common internal implementation.
   For example, the Pekko Stream Scala instances have a `.asJava` method to convert to the `org.apache.pekko.stream.javadsl` counterparts.

1. When using Scala `object` instances, offer a `getInstance()` method. See `org.apache.pekko.Done` for an example.

1. When the Scala API contains an `apply` method, use `create` or `of` for Java users.

1. Do not nest Scala objects to more than two levels.

1. Do not define traits nested in other classes or objects deeper than one level.

1. Be careful to convert values within data structures (eg. for `scala.Long` vs. `java.lang.Long`, use `scala.Long.box(value)`)

1. Complement any methods with Scala collections with a Java collection version

1. Use the `org.apache.pekko.japi.Pair` class to return tuples

1. If the underlying Scala code requires an `ExecutionContext`, make the Java API take an `Executor` and use
   `ExecutionContext.fromExecutor(executor)` for conversion.

1. Use `org.apache.pekko.util.FutureConverters` to translate `Future`s to `CompletionStage`s (or vice versa).

1. Use `org.apache.pekko.util.OptionConverters` to translate `Option`s to Java `Optional`s (or vice versa).

1. Use `org.apache.pekko.util.FunctionConverters` to translate Scala Functions to Java Functions (or vice versa).
 
1. Make sure there are Java tests or sample code touching all parts of the API

1. Do not use lower type bounds: `trait[T] { def method[U >: Something]: U }` as they do not work with Java

1. Provide `getX` style accessors for values in the Java APIs

1. Place classes not part of the public APIs in a shared `internal` package. This package can contain implementations of
   both Java and Scala APIs. Make such classes `private[pekko]` and also, since that becomes `public` from Java's point of
   view, annotate with `@InternalApi` and add a scaladoc saying `INTERNAL API`

1. Traits that are part of the Java API should only be used to define pure interfaces, as soon as there are implementations of methods, prefer
   `abstract class`.

1. Any method definition in a class that will be part of the Java API should not use any default parameters, as they will show up ugly when using them from Java. Use plain old method overloading instead.

#### Overview of Scala types and their Java counterparts

| Scala | Java |
|-------|------|
| `scala.Option[T]` | `java.util.Optional<T>` (`OptionalDouble`, ...) |
| `scala.collection.immutable.Seq[T]` | `java.util.List<T>` |
| `scala.concurrent.Future[T]` | `java.util.concurrent.CompletionStage<T>` |
| `scala.concurrent.Promise[T]` | `java.util.concurrent.CompletableFuture<T>` |
| `scala.concurrent.duration.FiniteDuration` | `java.time.Duration` (use `org.apache.pekko.util.JavaDurationConverters`) |
| `T => Unit` | `java.util.function.Consumer<T>` |
| `() => R` (`scala.Function0[R]`) | `java.util.function.Supplier<R>` |
| `T => R` (`scala.Function1[T, R]`) | `java.util.function.Function<T, R>` |

### Contributing new Pekko Streams operators

Documentation of Pekko Streams operators is automatically enforced.
If a method exists on Source / Sink / Flow, or any other class listed in `project/StreamOperatorsIndexGenerator.scala`,
it must also have a corresponding documentation page under `docs/src/main/paradox/streams/operators/...`.

Pekko Streams operators' consistency is enforced by `ConsistencySpec`, normally an operator should exist on both Source / SubSource, Flow / SubFlow, Sink / SubSink.

The pages structure is well-defined and must be the same on all documentation pages. Please refer to any neighbouring
docs pages in there to see the pattern in action. In general the page must consist of:

- the title, including where the operator is defined (e.g. `ActorFlow.ask` or `Source.map`)
- a short explanation of what this operator does, 1 sentence is optimal
- an image explaining the operator more visually (whenever possible)
- a link to the operators' "category" (these are listed in `docs/src/main/paradox/categories`)
- the method signature snippet (use the built in directives to generate it)
- a longer explanation about the operator and its exact semantics (when it pulls, cancels, signals elements)
- at least one usage example

By using this structure, the surrounding infrastructure will **generate the index pages**, so you do not need to maintain
the index or category pages manually.

#### Adding new top-level objects/classes containing operators

In case you are adding not only a new operator but also a new class/object, you need to add it to the
`project/StreamOperatorsIndexGenerator.scala` so it can be included in the automatic docs generation and enforcing the
existence of those docs.

## Supporting infrastructure

### Reporting security issues

If you have found an issue in a Pekko project that might have security implications, you can report it by following the process mentioned in the [Apache document](https://apache.org/security/#reporting-a-vulnerability). We will make sure those will get handled with priority. Thank you for your responsible disclosure!

### Continuous integration

Pekko currently uses Github Actions to run continuous integration. There are workflows for different purposes, such as:

* Validating pull requests
* Nightly builds
* Run a larger group of tests when pushing code to `main` branch.

Anyone can propose new changes to our CI workflows, and we will gladly review them as we do for regular pull-requests.

### Related links

* [Pekko Issue Tracker](https://github.com/apache/pekko/issues)
* [Scalafmt](https://scalameta.org/scalafmt/)
