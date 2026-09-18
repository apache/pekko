# Other Apache Pekko modules

This page describes modules that complement libraries from the Pekko core.  See [this overview]($pekko.doc.dns$/docs/pekko/current/typed/guide/modules.html) instead for a guide on the core modules.

## [Pekko HTTP]($pekko.doc.dns$/docs/pekko-http/current/)

A full server- and client-side HTTP stack on top of pekko-actor and pekko-stream.

## [Pekko gRPC]($pekko.doc.dns$/docs/pekko-grpc/current/)

Pekko gRPC provides support for building streaming gRPC servers and clients on top of Pekko Streams.

## [Pekko Connectors]($pekko.doc.dns$/docs/pekko-connectors/current/)

Pekko Connectors is a Reactive Enterprise Integration library for Java and Scala, based on Reactive Streams and Pekko.

## [Pekko Kafka Connector]($pekko.doc.dns$/docs/pekko-connectors-kafka/current/)

The Pekko Kafka Connector connects Apache Kafka with Pekko Streams.


## [Pekko Projections]($pekko.doc.dns$/docs/pekko-projection/current/)

Pekko Projections let you process a stream of events or records from a source to a projected model or external system.


## [Cassandra Plugin for Pekko Persistence]($pekko.doc.dns$/docs/pekko-persistence-cassandra/current/)

A Pekko Persistence journal and snapshot store backed by Apache Cassandra.


## [JDBC Plugin for Pekko Persistence]($pekko.doc.dns$/docs/pekko-persistence-jdbc/current/)

A Pekko Persistence journal and snapshot store for use with JDBC-compatible databases. This implementation relies on [Slick](https://scala-slick.org/).

## [R2DBC Plugin for Pekko Persistence]($pekko.doc.dns$/docs/pekko-persistence-r2dbc/current/)

A Pekko Persistence journal and snapshot store for use with R2DBC-compatible databases. This implementation relies on [R2DBC](https://r2dbc.io/).


## Apache Pekko Management

* @extref:[Pekko Management](pekko-management:) provides a central HTTP endpoint for Pekko management extensions.
* @extref:[Pekko Cluster Bootstrap](pekko-management:bootstrap/) helps bootstrapping a Pekko cluster using Pekko Discovery.
* @extref:[Pekko Management Cluster HTTP](pekko-management:cluster-http-management.html) provides HTTP endpoints for introspecting and managing Pekko clusters.
* @extref:[Pekko Discovery for Kubernetes, Consul, Marathon, and AWS](pekko-management:discovery/)
* @extref:[Kubernetes Lease](pekko-management:kubernetes-lease.html)
