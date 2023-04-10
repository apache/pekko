---
project.description: Apache Pekko version migration guides.
---
# Migration Guides

Apache Pekko is based on the latest version of Akka in the v2.6.x series. If migrating from an earlier version of Akka, 
please [migrate to Akka 2.6](https://doc.akka.io/docs/akka/current/project/migration-guides.html) before migrating to Pekko.

## Migration to Apache Pekko

This is just stub documentation. It will be improved.

In your dependency management files:
* Update dependency Akka version to the latest version of Pekko `1.0.x`.
* for Pekko jar dependencies, the groupId is `org.apache.pekko` instead of `com.typesafe.akka` or `com.lightbend.akka.<package_name>`
* the jar names start with "pekko" instead of "akka" - e.g. pekko-actor_2.13.jar instead of akka-actor_2.13.jar
* Alpakka equivalent is "pekko-connectors" - e.g. pekko-connectors-kafka_2.13.jar instead of alpakka-kafka_2.13.jar

In config files:
* Configs use `pekko` prefix instead of `akka`

In source code files:
* Pekko packages start with `org.apache.pekko` instead of `akka` - e.g. `import org.apache.pekko.actor` instead of `import akka.actor`
* Where class names have "Akka" in the name, the Pekko ones have "Pekko" - e.g. `PekkoException` instead of `AkkaException`
* Configs use `pekko` prefix instead of `akka`

In Documentation:
* Where appropriate, text should refer to `Pekko` instead of `Akka`
* URLs linking to files in https://doc.akka.io/docs/akka/ should instead point at https://pekko.apache.org/docs/pekko/

* We have changed the default ports used by the pekko-remote module.
    * With @ref:[Classic Remoting](../remoting.md), Akka defaults to 2552, while Pekko defaults to 7355.
    * With @ref:[Artery Remoting](../remoting-artery.md), Akka defaults to 25520, while Pekko defaults to 17355.

## TBC:
We are still investigating the effects of how the package name changes affect the @ref:[Persistence](../persistence.md)
and @ref:[Cluster](../cluster-usage.md) modules.

It appears that data persisted with "akka-persistence" is usable with "pekko-persistence" (and vice versa).

We currently do not expect that Akka and Pekko nodes will be able to form a cluster.

We may be able to provide [Scalafix](https://scalacenter.github.io/scalafix/) scripts to help with migrations.
