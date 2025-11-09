# Source.asSubscriber

Integration with Reactive Streams, materializes into a @javadoc[Subscriber](org.reactivestreams.Subscriber).

@ref[Source operators](../index.md#source-operators)

## Signature

Scala
:   @@snip[JavaFlowSupport.scala](/stream/src/main/scala/org/apache/pekko/stream/scaladsl/JavaFlowSupport.scala) { #asSubscriber }

Java
:   @@snip[AsSubscriber.java](/docs/src/test/java/jdocs/stream/operators/source/AsSubscriber.java) { #api }

## Description

If you want to create a @apidoc[Source] that gets its elements from another library that supports
[Reactive Streams](https://www.reactive-streams.org/), you can use `Source.asSubscriber`.
Each time this @apidoc[Source] is materialized, it produces a materialized value of type
@javadoc[org.reactivestreams.Subscriber](java.util.concurrent.Flow.Subscriber).
This @javadoc[Subscriber](org.reactivestreams.Subscriber) can be attached to a
[Reactive Streams](https://www.reactive-streams.org/) @javadoc[Publisher](org.reactivestreams.Publisher)
to populate it.

If the API you want to consume elements from provides a @javadoc[Publisher](org.reactivestreams.Publisher) instead of accepting a @javadoc[Subscriber](org.reactivestreams.Subscriber), see @ref[fromPublisher](fromPublisher.md).

In Java 9, the Reactive Stream API was included in the JDK, and `Subscriber` is available through [Flow.Subscriber](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.Subscriber.html).
Since those APIs are identical but exist at different package namespaces and does not depend on the Reactive Streams package a separate API for those is available 
through @scala[`org.apache.pekko.stream.scaladsl.JavaFlowSupport.Source#asSubscriber`]@java[`org.apache.pekko.stream.javadsl.JavaFlowSupport.Source#asSubscriber`].

## Example

Suppose we use a database client that supports [Reactive Streams](https://www.reactive-streams.org/),
we could create a @apidoc[Source] that queries the database for its rows. That @apidoc[Source] can then
be used for further processing, for example creating a @apidoc[Source] that contains the names of the
rows.

Note that since the database is queried for each materialization, the `rowSource` can be safely re-used.
Because both the database driver and Pekko Streams support [Reactive Streams](https://www.reactive-streams.org/),
backpressure is applied throughout the stream, preventing us from running out of memory when the database
rows are consumed slower than they are produced by the database.

Scala
:  @@snip [AsSubscriber.scala](/docs/src/test/scala/docs/stream/operators/source/AsSubscriber.scala) { #imports #example }

Java
:  @@snip [AsSubscriber.java](/docs/src/test/java/jdocs/stream/operators/source/AsSubscriber.java) { #imports #example }
