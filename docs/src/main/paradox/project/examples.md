# Example projects

The following example projects can be downloaded. They contain build files and have instructions
of how to run.

## Quickstart

@scala[[Quickstart Guide](https://github.com/apache/pekko-quickstart-scala.g8)]
@java[[Quickstart Guide](https://github.com/apache/pekko-quickstart-java.g8)]
 
The *Quickstart* guide walks you through example code that introduces how to define actor systems, actors, and
messages as well as how to use the test module and logging.

## FSM

@java[@extref[FSM example project](samples:pekko-sample-fsm-java)]
@scala[@extref[FSM example project](samples:pekko-sample-fsm-scala)]

This project contains a Dining Hakkers sample illustrating how to model a Finite State Machine (FSM) with actors.

## Cluster

@java[@extref[Cluster example project](samples:pekko-sample-cluster-java)]
@scala[@extref[Cluster example project](samples:pekko-sample-cluster-scala)]

This project contains samples illustrating different Cluster features, such as
subscribing to cluster membership events, and sending messages to actors running on nodes in the cluster
with Cluster aware routers.

It also includes Multi JVM Testing with the `sbt-multi-jvm` plugin.

## Distributed Data

@java[@extref[Distributed Data example project](samples:pekko-sample-distributed-data-java)]
@scala[@extref[Distributed Data example project](samples:pekko-sample-distributed-data-scala)]

This project contains several samples illustrating how to use Distributed Data.

## Cluster Sharding

@java[@extref[Sharding example project](samples:pekko-sample-sharding-java)]
@scala[@extref[Sharding example project](samples:pekko-sample-sharding-scala)]

This project contains a KillrWeather sample illustrating how to use Cluster Sharding.

## Persistence

@java[@extref[Persistence example project](samples:pekko-sample-persistence-java)]
@scala[@extref[Persistence example project](samples:pekko-sample-persistence-scala)]

This project contains a Shopping Cart sample illustrating how to use Pekko Persistence.

## Replicated Event Sourcing

@java[[Multi-DC Persistence example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-persistence-dc-java)]
@scala[[Multi-DC Persistence example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-persistence-dc-scala)]

Illustrates how to use @ref:[Replicated Event Sourcing](../typed/replicated-eventsourcing.md) that supports
active-active persistent entities across data centers.

## Cluster with Docker

@java[[Cluster with docker-compose example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-docker-compose-java)]
@scala[[Cluster with docker-compose example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-docker-compose-scala)]

Illustrates how to use Pekko Cluster with Docker compose.

## Cluster with Kubernetes

@java[[Cluster with Kubernetes example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-kubernetes-java)]
@scala[[Cluster with Kubernetes example project](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cluster-kubernetes-scala)]

This sample illustrates how to form a Pekko Cluster with Pekko Bootstrap when running in Kubernetes.

## Distributed workers

@extref[Distributed workers example project](samples:pekko-sample-distributed-workers-scala)

This project demonstrates the work pulling pattern using Pekko Cluster.

## gRPC on Kubernetes

@extref[gRPC on Kubernetes example project](samples:pekko-sample-grpc-kubernetes-scala)

This project demonstrates how to build services using gRPC module running on Kubernetes.

## Kafka to Cluster Sharding 

@extref[Kafka to Cluster Sharding example project](samples:pekko-sample-kafka-to-sharding-scala)

This project demonstrates how to use the External Shard Allocation strategy to co-locate the consumption of Kafka
partitions with the shard that processes the messages.
