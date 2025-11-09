# Source.fromPublisher

Integration with Reactive Streams, subscribes to a @javadoc[Java Flow Publisher](java.util.concurrent.Flow.Publisher) or a @javadoc[org.reactivestreams Publisher](org.reactivestreams.Publisher).

@ref[Source operators](../index.md#source-operators)

## Signature

Scala
:   @@snip[JavaFlowSupport.scala](/stream/src/main/scala/org/apache/pekko/stream/scaladsl/JavaFlowSupport.scala) { #fromPublisher }

Java
:   @@snip[JavaFlowSupport.java](/docs/src/test/java/jdocs/stream/operators/source/FromPublisher.java) { #api }


## Description

This source will produce the elements from the @javadoc[Java Flow Publisher](java.util.concurrent.Flow.Publisher) or the @javadoc[org.reactivestreams Publisher](org.reactivestreams.Publisher),
and coordinate backpressure as needed.

If the API you want to consume elements from accepts a @javadoc[Subscriber](java.util.concurrent.Flow.Subscriber) instead of providing a @javadoc[Publisher](java.util.concurrent.Flow.Publisher), see @ref[asJavaSubscriber](asJavaSubscriber.md).

@@@ note

Reactive Streams users: we prefer @javadoc[java.util.concurrent.Flow](java.util.concurrent.Flow) but you may still use the [org.reactivestreams](https://github.com/reactive-streams/reactive-streams-jvm#reactive-streams) library with @apidoc[Source.fromPublisher](Source$) { scala="#fromPublisher[T](publisher:org.reactivestreams.Publisher[T]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#fromPublisher(org.reactivestreams.Publisher)" }.

@@@

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
