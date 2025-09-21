# Sink.count

Counts all incoming elements until upstream terminates.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.count](Sink$) { scala="#count[T]:org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[Long]]" java="#count()" }


## Description

Counts values emitted from the stream, the count is available through a @scala[`Future`] @java[`CompletionStage`] or
which completes when the stream completes. 

## Example

Given a stream of numbers we can count the numbers with the `count` operator

Scala
:   @@snip [SinkSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/SinkSpec.scala) { #count-operator-example }

Java
:   @@snip [SinkDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SinkDocExamples.java) { #count-operator-example }

## Reactive Streams semantics

@@@div { .callout }

**completes** when upstream completes

**backpressures** never (counting is a lightweight operation)

**cancels** never

@@@


