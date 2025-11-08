# Sink.asPublisher

Integration with Reactive Streams, materializes into a `org.reactivestreams.Publisher`.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.asPublisher](Sink$) { scala="#asPublisher[T](fanout:Boolean):org.apache.pekko.stream.scaladsl.Sink[T,org.reactivestreams.Publisher[T]]" java="#asPublisher(org.apache.pekko.stream.javadsl.AsPublisher)" }



## Description

This method gives you the capability to publish the data from the `Sink` through a Reactive Streams [Publisher](https://www.reactive-streams.org/reactive-streams-1.0.3-javadoc/org/reactivestreams/Publisher.html).
Generally, in Pekko Streams a `Sink` is considered a subscriber, which consumes the data from source. To integrate with other Reactive Stream implementations `Sink.asPublisher` provides a `Publisher` materialized value when run.
Now, the data from this publisher can be consumed by subscribing to it. We can control if we allow more than one downstream subscriber from the single running Pekko stream through the `fanout` parameter.
If you want to support [Flow.Publisher](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Flow.Publisher.html), there is [Sink.asJavaPublisher](asJavaPublisher.md).

## Example

In the example we are using a source and then creating a Publisher. After that, we see that when `fanout` is true multiple subscribers can subscribe to it, 
but when it is false only the first subscriber will be able to subscribe and others will be rejected.

Scala
:   @@snip [AsPublisher.scala](/docs/src/test/scala/docs/stream/operators/sink/AsPublisher.scala) { #asPublisher }

Java
:   @@snip [SinkDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SinkDocExamples.java) { #asPublisher }

## Reactive Streams semantics

@@@div { .callout }

**emits** the materialized publisher

**completes** after the source is consumed and materialized publisher is created

@@@
