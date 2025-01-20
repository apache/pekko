# Sink.none

A `Sink` that will test the given predicate `p` for every received element and completes with the result.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.none](Sink$) { scala="#none[T](p:T=%3EBoolean):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[Boolean]]" java="#none(org.apache.pekko.japi.function.Predicate)" }

## Description
none operator applies a predicate function to assert each element received, it returns false if any element satisfy the assertion, otherwise it returns true.

It materializes into a `Future` (in Scala) or a `CompletionStage` (in Java) that completes with the last state when the stream has finished.

Notes that if source is empty, it will return true

A `Sink` that will test the given predicate `p` for every received element and

 - completes and returns  @scala[`Future`] @java[`CompletionStage`] of `true` if the predicate is false for all elements; 
 - completes and returns  @scala[`Future`] @java[`CompletionStage`] of `true` if the stream is empty (i.e. completes before signalling any elements); 
 - completes and returns  @scala[`Future`] @java[`CompletionStage`] of `false` if the predicate is true for any element.

The materialized value @scala[`Future`] @java[`CompletionStage`] will be completed with the value `true` or `false`
when the input stream ends, or completed with `Failure` if there is a failure signaled in the stream.

## Example

This example tests all elements in the stream is `<=` 100.

Scala
:   @@snip [NoneMatch.scala](/docs/src/test/scala/docs/stream/operators/sink/NoneMatch.scala) { #none }

Java
:   @@snip [NoneMatch.java](/docs/src/test/java/jdocs/stream/operators/sink/NoneMatch.java) { #none }

## Reactive Streams Semantics

@@@div { .callout }

***Completes*** when upstream completes or the predicate `p` returns `true`

**cancels** when predicate `p` returns `true`

**backpressures** when the invocation of predicate `p` has not yet completed

@@@
