# Source.fromPublisher

Integration with Reactive Streams, subscribes to a @javadoc[Publisher](org.reactivestreams.Publisher).

@ref[Source operators](../index.md#source-operators)

## Signature

Scala
:   @@snip[JavaFlowSupport.scala](/stream/src/main/scala/org/apache/pekko/stream/scaladsl/JavaFlowSupport.scala) { #fromPublisher }

Java
:   @@snip[JavaFlowSupport.java](/docs/src/test/java/jdocs/stream/operators/source/FromPublisher.java) { #api }


## Description

If you want to create a @apidoc[Source] that gets its elements from another library that supports
[Reactive Streams](https://www.reactive-streams.org/), you can use `Source.fromPublisher`.
This source will produce the elements from the @javadoc[Publisher](org.reactivestreams.Publisher),
and coordinate backpressure as needed.

If the API you want to consume elements from accepts a @javadoc[Subscriber](org.reactivestreams.Subscriber) instead of providing a @javadoc[Publisher](org.reactivestreams.Publisher), see @ref[asSubscriber](asSubscriber.md).

In Java 9, the Reactive Stream API was included in the JDK, and `Publisher` is available through [Flow.Publisher](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.Publisher.html).
Since those APIs are identical but exist at different package namespaces and does not depend on the Reactive Streams package a separate API for those is available 
through @scala[`org.apache.pekko.stream.scaladsl.JavaFlowSupport.Source#fromPublisher`]@java[`org.apache.pekko.stream.javadsl.JavaFlowSupport.Source#fromPublisher`].

## Example

Suppose we use a database client that supports [Reactive Streams](https://www.reactive-streams.org/),
we could create a @apidoc[Source] that queries the database for its rows. That @apidoc[Source] can then
be used for further processing, for example creating a @apidoc[Source] that contains the names of the
rows.

Because both the database driver and Pekko Streams support [Reactive Streams](https://www.reactive-streams.org/),
backpressure is applied throughout the stream, preventing us from running out of memory when the database
rows are consumed slower than they are produced by the database.

Scala
:  @@snip [FromPublisher.scala](/docs/src/test/scala/docs/stream/operators/source/FromPublisher.scala) { #imports #example }

Java
:  @@snip [FromPublisher.java](/docs/src/test/java/jdocs/stream/operators/source/FromPublisher.java) { #imports #example }
