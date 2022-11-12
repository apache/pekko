# Sink.combine

Combine several sinks into one using a user specified strategy

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.combine](Sink$) { scala="#combine[T,U](first:org.apache.pekko.stream.scaladsl.Sink[U,_],second:org.apache.pekko.stream.scaladsl.Sink[U,_],rest:org.apache.pekko.stream.scaladsl.Sink[U,_]*)(strategy:Int=&gt;org.apache.pekko.stream.Graph[org.apache.pekko.stream.UniformFanOutShape[T,U],org.apache.pekko.NotUsed]):org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.NotUsed]" java="#combine(org.apache.pekko.stream.javadsl.Sink,org.apache.pekko.stream.javadsl.Sink,java.util.List,org.apache.pekko.japi.function.Function)" }

## Description

Combine several sinks into one using a user specified strategy

## Example

This example shows how to combine multiple sinks with a Fan-out Junction.

Scala
:   @@snip [StreamPartialGraphDSLDocSpec.scala](/akka-docs/src/test/scala/docs/stream/StreamPartialGraphDSLDocSpec.scala) {#sink-combine }

Java
:   @@snip [StreamPartialGraphDSLDocTest.java](/akka-docs/src/test/java/jdocs/stream/StreamPartialGraphDSLDocTest.java) { #sink-combine }

## Reactive Streams semantics

@@@div { .callout }

**cancels** depends on the strategy

**backpressures** depends on the strategy

@@@

