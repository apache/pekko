# Sink.fromSubscriber

Integration with Reactive Streams, wraps a `org.reactivestreams.Subscriber` as a sink.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.fromSubscriber](Sink$) { scala="#fromSubscriber[T](subscriber:org.reactivestreams.Subscriber[T]):org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.NotUsed]" java="#fromSubscriber(org.reactivestreams.Subscriber)" }


## Description

In Java 9, the Reactive Stream API was included in the JDK, and `Subscriber` is available through [Flow.Subscriber](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.Subscriber.html).
Since those APIs are identical but exist at different package namespaces and does not depend on the Reactive Streams package a separate publisher sink for those is available 
through @scala[`org.apache.pekko.stream.scaladsl.JavaFlowSupport.Sink#fromSubscriber`]@java[`org.apache.pekko.stream.javadsl.JavaFlowSupport.Sink#fromSubscriber`].
