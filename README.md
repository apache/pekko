# Apache Pekko

[![Nightly Builds](https://github.com/apache/pekko/actions/workflows/nightly-builds.yml/badge.svg)](https://github.com/apache/pekko/actions/workflows/nightly-builds.yml)
[![Nightly Aeron Tests](https://github.com/apache/pekko/actions/workflows/nightly-builds-aeron.yml/badge.svg?branch=main)](https://github.com/apache/pekko/actions/workflows/nightly-builds-aeron.yml)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/9032/badge)](https://www.bestpractices.dev/projects/9032)

Apache Pekko is an open-source framework for building applications that are concurrent, distributed, resilient and elastic.
Pekko uses the Actor Model to provide more intuitive high-level abstractions for concurrency.
Using these abstractions, Pekko also provides libraries for persistence, streams, HTTP, and more.

Pekko is a fork of [Akka](https://github.com/akka/akka) 2.6.x, prior to the Akka project's adoption of the Business Source License.

## Reference Documentation

See https://pekko.apache.org for the documentation including the API docs. The docs for all the Apache Pekko modules can be found there.

## Building from Source

The CI build is Linux based (Ubuntu) and most Pekko developers use Macs or Linux machines. There have been reports of issues when building with Windows ([#829](https://github.com/apache/pekko/issues/829)).

## Using IntelliJ IDEA

To use IntelliJ IDEA, you need to configure your project to use JDK 8. For a visual guide, refer to this [Q&A](https://github.com/apache/pekko/discussions/1847#discussioncomment-13166066).

- File > Project Structure > Project Settings > Project > Set your JDK to version 17
- Settings > ... > Scala Compiler Server > Set your JDK to version 17 
- Settings > ... > sbt > Set your JRE to version 17

### Prerequisites
- Make sure you have installed a Java Development Kit (JDK) version 17 or later.
- Make sure you have [sbt](https://www.scala-sbt.org/) installed and using this JDK.
- [Graphviz](https://graphviz.gitlab.io/download/) is needed for the scaladoc generation build task, which is part of the release.

### Running the Build
- Open a command window and change directory to your preferred base directory
- Use git to clone the [repo](https://github.com/apache/pekko) or download a source release from https://pekko.apache.org (and unzip or untar it, as appropriate)
- Change directory to the directory where you installed the source (you should have a file called `build.sbt` in this directory)
- `sbt compile` compiles the main source for project default version of Scala (2.13)
    - `sbt +compile` will compile for all supported versions of Scala
- `sbt test` will compile the code and run the unit tests
- `sbt testQuick` similar to test but when repeated in shell mode will only run failing tests
- `sbt testQuickUntilPassed` similar to testQuick but will loop until tests pass.
- `sbt package` will build the jars
    - the jars will be built into target dirs of the various modules
    - for the 'actor' module, the jar will be built to `actor/target/scala-2.13/`
- `sbt publishLocal` will push the jars to your local Apache Ivy repository
- `sbt publishM2` will push the jars to your local Apache Maven repository
- `sbt docs/paradox` will build the docs (the ones describing the module features)
     - Requires Java 11 minimum
     - `sbt docs/paradoxBrowse` does the same but will open the docs in your browser when complete
     - the `index.html` file will appear in `target/paradox/site/main/`
- `sbt unidoc` will build the Javadocs for all the modules and load them to one place (may require Graphviz, see Prerequisites above)
     - the `index.html` file will appear in `target/scala-2.13/unidoc/`
- `sbt sourceDistGenerate` will generate source release to `target/dist/`
- The version number that appears in filenames and docs is derived, by default. The derived version contains the most git commit id or the date/time (if the directory is not under git control). 
    - You can set the version number explicitly when running sbt commands
        - eg `sbt "set ThisBuild / version := \"1.0.0\"; sourceDistGenerate"`  
    - Or you can add a file called `version.sbt` to the same directory that has the `build.sbt` containing something like
        - `ThisBuild / version := "1.0.0"` 

## Community

There are several ways to interact with the Pekko community:

- [GitHub discussions](https://github.com/apache/pekko/discussions): for questions and general discussion.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [Pekko users mailing list](https://lists.apache.org/list.html?users@pekko.apache.org): for Pekko user discussions.
- [GitHub issues](https://github.com/apache/pekko/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

## Contributing

Contributions are very welcome. If you have an idea on how to improve Pekko, don't hesitate to create an issue or submit a pull request.

See [CONTRIBUTING.md](https://github.com/apache/pekko/blob/main/CONTRIBUTING.md) for details on the development workflow and how to create your pull request.

## Code of Conduct

Apache Pekko is governed by the [Apache code of conduct](https://www.apache.org/foundation/policies/conduct.html). By participating in this project you agree to abide by its terms.

## License

Apache Pekko is available under the Apache License, version 2.0. See [LICENSE](https://github.com/apache/pekko/blob/main/LICENSE) file for details.
