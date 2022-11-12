# Sink.foreach

Invoke a given procedure for each element received.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.foreach](Sink$) { java="#foreach(org.apache.pekko.japi.function.Procedure)" scala="#foreach[T](f:T=%3EUnit):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[org.apache.pekko.Done]]" }

## Description

Invoke a given procedure for each element received. Note that it is not safe to mutate shared state from the procedure.

The sink materializes into a @scala[`Future[Done]`] @java[`CompletionStage<Done>`] which completes when the
stream completes, or fails if the stream fails.

Note that it is not safe to mutate state from the procedure.

See also:

* @ref[`foreachAsync`](foreachAsync.md) Invoke a given procedure asynchronously for each element received.
* @ref[`actorRef`](actorRef.md) Send the elements from the stream to an `ActorRef`.

## Example

This prints out every element to standard out.

Scala
:   @@snip [snip](/akka-stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/SinkSpec.scala) { #foreach }

Java
:   @@snip [snip](/akka-stream-tests/src/test/java/org/apache/pekko/stream/javadsl/SinkTest.java) { #foreach }

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** when the previous procedure invocation has not yet completed

@@@


