Apache Pekko
============

[![Nightly Builds](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds.yml/badge.svg)](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds.yml)
[![Nightly Aeron Tests](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds-aeron.yml/badge.svg?branch=main)](https://github.com/apache/incubator-pekko/actions/workflows/nightly-builds-aeron.yml)

Apache Pekko is an open-source framework for building applications that are concurrent, distributed, resilient and elastic. It uses the Actor Model to provide more intuitive high-level abstractions for concurrency.
Using these abstractions, Pekko also provides libraries for persistence, streams, HTTP, and more.

Pekko is part of the Apache Incubator program and we are actively working towards a v1.0.0 release. It's not yet ready for production use, but please get involved with the community and contribute to the effort.

Pekko is a fork of [Akka](https://github.com/akka/akka) 2.6.x, prior to the Akka project's adoption of the Business Source License.

Reference Documentation
-----------------------

The reference documentation is available at [pekko.apache.org](https://pekko.apache.org/).

Repositories
------------

The Apache Pekko project is formed into several repositories:

- [incubator-pekko](https://github.com/apache/incubator-pekko) (this repository): contains the core Apache Pekko framework.
- [incubator-pekko-connectors](https://github.com/apache/incubator-pekko-connectors) (WIP): contains connectors for other systems, such as Kafka, Cassandra, etc.
    - [incubator-pekko-connectors-kafka](https://github.com/apache/incubator-pekko-connectors-kafka): contains the Kafka connector.
    - [incubator-pekko-connectors-samples](https://github.com/apache/incubator-pekko-connectors-samples) (WIP): contains a sample connector.
- [incubator-pekko-grpc](https://github.com/apache/incubator-pekko-grpc): contains the gRPC server module.
- [incubator-pekko-http](https://github.com/apache/incubator-pekko-http): contains the HTTP server module.
- [incubator-pekko-management](https://github.com/apache/incubator-pekko-management): contains the tools for operating with Pekko clusters.
- [incubator-pekko-persistence-cassandra](https://github.com/apache/incubator-pekko-persistence-cassandra): contains the Cassandra persistence module.
- [incubator-pekko-persistence-jdbc](https://github.com/apache/incubator-pekko-persistence-jdbc): contains the JDBC persistence module.
- [incubator-pekko-persistence-r2dbc](https://github.com/apache/incubator-pekko-persistence-r2dbc): contains the R2DBC persistence module.
- [incubator-pekko-projection](https://github.com/apache/incubator-pekko-projection): contains the event sourcing and CQRS module.

In addition to the above, there are also the following quickstart templates:
- [Scala quickstart](https://github.com/apache/incubator-pekko-quickstart-scala.g8)
- [Java quickstart](https://github.com/apache/incubator-pekko-quickstart-java.g8)


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
