---
project.description: Apache Pekko version migration guides.
---
# Migration Guides

Apache Pekko is based on the latest version of Akka in the v2.6.x series. If migrating from an earlier version of Akka, 
please [migrate to Akka 2.6](https://doc.akka.io/docs/akka/current/project/migration-guides.html) before migrating to Pekko.

## Migration to Apache Pekko

This is just stub documentation. It will be improved.

* for Pekko jar dependencies, the groupId is "org.apache.pekko" instead of "com.typesafe.akka"
* the jar names start with "pekko" instead of "akka" - e.g. pekko-actor_2.13.jar instead of pekko-actor_2.13.jar 
* Alpakka equivalent is "pekko-connectors" - e.g. pekko-connectors-kafka_2.13.jar instead of alpakka-kafka_2.13.jar
* Pekko packages start with "org.apache.pekko" instead of "akka" - e.g. `import org.apache.pekko.actor` instead of `import akka.actor`
* Where class names have "Akka" in the name, the Pekko ones have "Pekko" - e.g. PekkoException instead of AkkaException
* Configs in `application.conf` use "pekko" prefix instead of "akka"

We are still investigating the effects of how the package name changes affect the @ref:[Persistence](../persistence.md)
and @ref:[Cluster](../cluster-usage.md) modules.
Data persisted with "akka-persistence" may not yet be usable with "pekko-persistence" (or vice versa).
Akka and Pekko nodes may not be able to form a cluster or messages may not be recognised if passed between nodes
of different types.

We may be able to provide [Scalafix](https://scalacenter.github.io/scalafix/) scripts to help with migrations.
