# Sink.seq

Collect values emitted from the stream into a collection.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.seq](Sink$) { scala="#seq[T]:org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[scala.collection.immutable.Seq[T]]]" java="#seq()" }


## Description

Collect values emitted from the stream into a collection, the collection is available through a @scala[`Future`] @java[`CompletionStage`] or
which completes when the stream completes. Note that the collection is bounded to @scala[`Int.MaxValue`] @java[`Integer.MAX_VALUE`],
if more element are emitted the sink will cancel the stream

## Example

Given a stream of numbers we can collect the numbers into a collection with the `seq` operator

Scala
:   @@snip [SinkSpec.scala](/akka-stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/SinkSpec.scala) { #seq-operator-example }

Java
:   @@snip [SinkDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SinkDocExamples.java) { #seq-operator-example }

## Reactive Streams semantics

@@@div { .callout }

**cancels** If too many values are collected

@@@


