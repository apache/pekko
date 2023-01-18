# Other Pekko modules

This page describes modules that compliment libraries from the Pekko core.  See [this overview](https://doc.akka.io/docs/akka/current/typed/guide/modules.html) instead for a guide on the core modules.

## [Pekko HTTP](https://doc.akka.io/docs/akka-http/current/)

A full server- and client-side HTTP stack on top of pekko-actor and pekko-stream.

## [Pekko gRPC](https://doc.akka.io/docs/akka-grpc/current/)

Pekko gRPC provides support for building streaming gRPC servers and clients on top of Pekko Streams.

## [Pekko Connectors](https://doc.akka.io/docs/alpakka/current/)

Pekko Connectors is a Reactive Enterprise Integration library for Java and Scala, based on Reactive Streams and Pekko.

## [Pekko Kafka Connector](https://doc.akka.io/docs/alpakka-kafka/current/)

The Pekko Kafka Connector connects Apache Kafka with Pekko Streams.


## [Pekko Projections](https://doc.akka.io/docs/akka-projection/current/)

Pekko Projections let you process a stream of events or records from a source to a projected model or external system.


## [Cassandra Plugin for Pekko Persistence](https://doc.akka.io/docs/akka-persistence-cassandra/current/)

An Pekko Persistence journal and snapshot store backed by Apache Cassandra.


## [JDBC Plugin for Pekko Persistence](https://doc.akka.io/docs/akka-persistence-jdbc/current/)

An Pekko Persistence journal and snapshot store for use with JDBC-compatible databases. This implementation relies on [Slick](https://scala-slick.org/).

## [R2DBC Plugin for Pekko Persistence](https://doc.akka.io/docs/akka-persistence-r2dbc/current/)

A Pekko Persistence journal and snapshot store for use with R2DBC-compatible databases. This implementation relies on [R2DBC](https://r2dbc.io/).

## [Google Cloud Spanner Plugin for Pekko Persistence](https://doc.akka.io/docs/akka-persistence-spanner/current/)

Use [Google Cloud Spanner](https://cloud.google.com/spanner/) as Pekko Persistence journal and snapshot store. This integration relies on [Pekko gRPC](https://doc.akka.io/docs/akka-grpc/current/).


## Pekko Management

* [Pekko Management](https://doc.akka.io/docs/akka-management/current/) provides a central HTTP endpoint for Pekko management extensions.
* [Pekko Cluster Bootstrap](https://doc.akka.io/docs/akka-management/current/bootstrap/) helps bootstrapping an Pekko cluster using Pekko Discovery.
* [Pekko Management Cluster HTTP](https://doc.akka.io/docs/akka-management/current/cluster-http-management.html) provides HTTP endpoints for introspecting and managing Pekko clusters.
* [Pekko Discovery for Kubernetes, Consul, Marathon, and AWS](https://doc.akka.io/docs/akka-management/current/discovery/)
* [Kubernetes Lease](https://doc.akka.io/docs/akka-management/current/kubernetes-lease.html)

## Pekko Resilience Enhancements

* [Pekko Thread Starvation Detector](https://doc.akka.io/docs/akka-enhancements/current/starvation-detector.html)
* [Pekko Configuration Checker](https://doc.akka.io/docs/akka-enhancements/current/config-checker.html)
* [Pekko Diagnostics Recorder](https://doc.akka.io/docs/akka-enhancements/current/diagnostics-recorder.html)

## Pekko Persistence Enhancements

* [Pekko GDPR for Persistence](https://doc.akka.io/docs/akka-enhancements/current/gdpr/index.html)

