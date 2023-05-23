Apache Pekko
============

[![Nightly Builds](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds.yml/badge.svg)](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds.yml)
[![Nightly Aeron Tests](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds-aeron.yml/badge.svg?branch=main)](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds-aeron.yml)

Apache Pekko is an open-source framework for building applications that are concurrent, distributed, resilient and elastic.
Pekko uses the Actor Model to provide more intuitive high-level abstractions for concurrency.
Using these abstractions, Pekko also provides libraries for persistence, streams, HTTP, and more.

Pekko is a fork of [Akka](https://github.com/akka/akka) 2.6.x, prior to the Akka project's adoption of the Business Source License.

Reference Documentation
-----------------------

See https://pekko.apache.org for the documentation including the API docs. The docs for all the Apache Pekko modules can be found there.

Building from Source
--------------------

- You will need to install [sbt](https://www.scala-sbt.org/) if you don't already have it installed
- You should have a Java Runtime installed. Java 8 minimum.
- Use git to clone the [repo](https://github.com/apache/incubator-pekko) or download a source release from https://pekko.apache.org
- Open a command window and change directory to the directory where you installed the source
- `sbt compile` compiles the main source for project default version of Scala (2.13)
    - `sbt +compile` will compile for all supported versions of Scala
- `sbt test` will compile the code and run the unit tests
- `sbt package` will build the jars
    - the jars will built into target dirs of the various modules
    - for the the 'actor' module, the jar will be built to `actor/target/scala-2.13/`
- `sbt publishLocal` will push the jars to your local Apache Ivy repository
- `sbt publishM2` will push the jars to your local Apache Maven repository
- `sbt docs/paradox` will build the docs (the ones describing the module features)
     - Requires Java 11 minimum
     - `sbt docs/paradoxBrowse` does the same but will open the docs in your browser when complete
     - the `index.html` file will appear in `target/paradox/site/main/`
- `sbt unidoc` will build the Javadocs for all the modules and load them to one place
     - the `index.html` file will appear in `target/scala-2.13/unidoc/`

Community
---------

There are several ways to interact with the Pekko community:

- [GitHub discussions](https://github.com/apache/incubator-pekko/discussions): for questions and general discussion.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [Pekko users mailing list](https://lists.apache.org/list.html?users@pekko.apache.org): for Pekko user discussions.
- [GitHub issues](https://github.com/apache/incubator-pekko/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

Contributing
------------

Contributions are very welcome. If you have an idea on how to improve Pekko, don't hesitate to create an issue or submit a pull request.

See [CONTRIBUTING.md](https://github.com/apache/incubator-pekko/blob/main/CONTRIBUTING.md) for details on the development workflow and how to create your pull request.

Code of Conduct
---------------

Apache Pekko is governed by the [Apache code of conduct](https://www.apache.org/foundation/policies/conduct.html). By participating in this project you agree to abide by its terms.

License
-------

Apache Pekko is available under the Apache License, version 2.0. See [LICENSE](https://github.com/apache/incubator-pekko/blob/main/LICENSE) file for details.
