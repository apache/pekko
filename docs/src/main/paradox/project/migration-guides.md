---
project.description: Apache Pekko version migration guides.
---
# Migration Guides

Apache Pekko is based on the latest version of Akka in the v2.6.x series. If migrating from an earlier version of Akka, 
please [migrate to Akka 2.6](https://doc.akka.io/docs/akka/current/project/migration-guides.html) before migrating to Pekko.

## Migration to Apache Pekko

This is just stub documentation. It will be improved.

* for Pekko jar dependencies, the groupId is "org.apache.pekko" instead of "com.typesafe.akka"
* the jar names start with "pekko" instead of "akka" - e.g. pekko-actor_2.13.jar instead of akka-actor_2.13.jar 
* Alpakka equivalent is "pekko-connectors" - e.g. pekko-connectors-kafka_2.13.jar instead of alpakka-kafka_2.13.jar
* Pekko packages start with "org.apache.pekko" instead of "akka" - e.g. `import org.apache.pekko.actor` instead of `import akka.actor`
* Where class names have "Akka" in the name, the Pekko ones have "Pekko" - e.g. PekkoException instead of AkkaException
* Configs in `application.conf` use "pekko" prefix instead of "akka"
* We have changed the default ports used by the pekko-remote module.
* With @ref:[Classic Remoting](../remoting.md), Akka defaults to 2552, while Pekko defaults to 7355.
* With @ref:[Artery Remoting](../remoting-artery.md), Akka defaults to 25520, while Pekko defaults to 17355.
* The Scala 2.13/Scala 3 version of Pekko no longer includes [scala-java8-compat](https://github.com/scala/scala-java8-compat)
  as a dependency. This means if you were relying on `scala-java8-compat` along with Scala 2.12/Scala 3 as a transitive 
  dependency it's recommended to migrate to using [`scala.jdk` instead](https://github.com/scala/scala-java8-compat#do-you-need-this).
  If this is not possible/desired then you can add `scala-java8-compat` as dependency yourself.
* In addition to the previous point, for Scala 2.12 `scala-java8-compat` has been updated to `1.0.2`. If you are using
  an older binary incompatible version of `scala-java8-compat` its recommend to update to `1.0.2`.
* For Scala 2.12 and 2.13 [ssl-config](https://github.com/lightbend/ssl-config) has been updated to 0.6.1 in order
  to bring in bug/security fixes. Note that ssl-config 0.6.1 is binary and source compatible with 0.4.1.

We are still investigating the effects of how the package name changes affect the @ref:[Persistence](../persistence.md)
and @ref:[Cluster](../cluster-usage.md) modules.

It appears that data persisted with "akka-persistence" is usable with "pekko-persistence" (and vice versa).

We currently do not expect that Akka and Pekko nodes will be able to form a cluster.

We may be able to provide [Scalafix](https://scalacenter.github.io/scalafix/) scripts to help with migrations.
