---
project.description: Apache Pekko version migration guides.
---
# Migration Guides

Apache Pekko is based on the latest version of Akka in the v2.6.x series. If migrating from an earlier version of Akka, 
please [migrate to Akka 2.6](https://doc.akka.io/docs/akka/current/project/migration-guides.html) before migrating to Pekko.

## Migration to Apache Pekko

These migration notes are designed for users migrating from Akka 2.6 to Pekko 1.0 and assume a basic level of Akka experience. Please feel free to submit an issue or a patch if you feel like the notes can be improved.

* for Pekko jar dependencies, the groupId is "org.apache.pekko" instead of "com.typesafe.akka"
* the jar names start with "pekko" instead of "akka" - e.g. pekko-actor_2.13.jar instead of akka-actor_2.13.jar 
* Alpakka equivalent is "pekko-connectors" - e.g. pekko-connectors-kafka_2.13.jar instead of alpakka-kafka_2.13.jar
* Pekko packages start with "org.apache.pekko" instead of "akka" - e.g. `import org.apache.pekko.actor` instead of `import akka.actor`
* Where class names have "Akka" in the name, the Pekko ones have "Pekko" - e.g. PekkoException instead of AkkaException
* Config names use "pekko" prefix instead of "akka", e.g. `pekko.actor.provider` instead of `akka.actor.provider`
* The Pekko node URLs use different URL schemes.
    * `pekko://` instead of `akka://`
    * `pekko.tcp://` instead of `akka.tcp://`
* We have changed the default ports used by the pekko-remote module.
    * With @ref:[Classic Remoting](../remoting.md), Akka defaults to 2552, while Pekko defaults to 7355.
    * With @ref:[Artery Remoting](../remoting-artery.md), Akka defaults to 25520, while Pekko defaults to 17355.
* Cluster Management users should read the pekko-management [migration guide]($pekko.doc.dns$/docs/pekko-management/current/migration.html)

### Dependency Changes
* The Scala 2.13/Scala 3 versions of Pekko no longer include [scala-java8-compat](https://github.com/scala/scala-java8-compat)
  as a dependency. This means if you were relying on `scala-java8-compat` along with Scala 2.12/Scala 3 as a transitive 
  dependency that it's recommended to migrate to using [`scala.jdk` instead](https://github.com/scala/scala-java8-compat#do-you-need-this).
  If this is not possible/desired then you can add `scala-java8-compat` as dependency yourself.
* In addition to the previous point, for Scala 2.12 `scala-java8-compat` has been updated to `1.0.2`. If you are using
  an older binary incompatible version of `scala-java8-compat` then it is recommended to update to `1.0.2`.
* For Scala 2.12 and 2.13 [ssl-config](https://github.com/lightbend/ssl-config) has been updated to 0.6.1 in order
  to bring in bug/security fixes. Note that ssl-config 0.6.1 is binary and source compatible with 0.4.1.
* Pekko TestKit users will find that scalatest 3.2 is the default dependency version. Users with old Akka TestKit based tests may need
  to migrate some of their test code. The [scalatest 3.2 release notes](https://www.scalatest.org/release_notes/3.2.0) have a detailed
  description of the changes needed.   
* Jackson is upgraded to 2.14.3.
* reactivestreams is upgraded to 1.0.4.
* pekko-protobuf-v3 is based on protobuf-java 3.16.3.

### Miscellaneous Notes

Data persisted with "akka-persistence" is usable with "pekko-persistence" and vice versa (@ref:[Persistence](../persistence.md)). There is one [issue](https://github.com/apache/pekko/pull/837) that is fixed in v1.0.3 (related to persisted snapshots - for which there is a [workaround](https://github.com/scullxbones/pekko-persistence-mongo/pull/14#issuecomment-1847223850)).

Early releases of Apache Pekko could not be used to allow Akka and Pekko nodes to combine to form a @ref:[cluster](../cluster-usage.md). Newer versions @ref:[release](../release-notes/index.md) have experimental support that we would appreciate users to try out in test environments ([wiki page](https://cwiki.apache.org/confluence/display/PEKKO/Pekko+Akka+Compatibility)).
