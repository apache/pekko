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

See https://pekko.apache.org for the documentation including the API docs.

Building from Source
--------------------

* You will need to install [sbt](https://www.scala-sbt.org/) if you don't already have it installed
* Use git to clone the [repo](https://github.com/apache/incubator-pekko) or download a source release from https://pekko.apache.org
* Open a command window and change directory to the directory where you installed the source
* `sbt test` will build the jars and run the unit tests
* `sbt docs/paradox` will build the docs

Community
---------

There are several ways to interact with the Pekko community:

- [GitHub discussions](https://github.com/apache/incubator-pekko/discussions): for questions and general discussion.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
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
