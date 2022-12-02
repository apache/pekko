# Sink.foreachAsync

Invoke a given procedure asynchronously for each element received.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.foreachAsync](Sink$) { scala="#foreachAsync[T](parallelism:Int)(f:T=&gt;scala.concurrent.Future[Unit]):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[org.apache.pekko.Done]]" java="#foreachAsync(int,org.apache.pekko.japi.function.Function)" }


## Description

Invoke a given procedure asynchronously for each element received. Note that if shared state is mutated from the procedure that must be done in a thread-safe way.

The sink materializes into a  @scala[`Future[Done]`] @java[`CompletionStage<Done>`] which completes when the
stream completes, or fails if the stream fails.

See also:

* @ref[`foreach`](foreach.md) Invoke a given procedure for each element received.
* @ref[`actorRef`](actorRef.md) Send the elements from the stream to an `ActorRef`.

## Example

Scala
:   @@snip [SinkRecipeDocSpec.scala](/docs/src/test/scala/docs/stream/SinkRecipeDocSpec.scala) { #forseachAsync-processing }

Java
:   @@snip [SinkRecipeDocTest.java](/docs/src/test/java/jdocs/stream/SinkRecipeDocTest.java) { #forseachAsync-processing }

## Reactive Streams semantics

@@@div { .callout }

**cancels** when a @scala[`Future`] @java[`CompletionStage`] fails

**backpressures** when the number of @scala[`Future`s] @java[`CompletionStage`s] reaches the configured parallelism

@@@


