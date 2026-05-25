# StreamConverters.javaCollector

Create a sink which materializes into a @scala[`Future`] @java[`CompletionStage`] which will be completed with a result of the Java `Collector` transformation and reduction operations.

@ref[Additional Sink and Source converters](../index.md#additional-sink-and-source-converters)

## Signature

@apidoc[StreamConverters.javaCollector](StreamConverters$) { scala="#javaCollector[T,R](collectorFactory:()=&gt;java.util.stream.Collector[T,_,R]):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[R]]" java="#javaCollector(org.apache.pekko.japi.function.Creator)" }


## Description

`javaCollector` creates a @apidoc[Sink] that accepts a Java 8 @javadoc[java.util.stream.Collector](java.util.stream.Collector)
factory. The `Collector` accumulates incoming elements into a mutable container as downstream demand is
triggered, and after the stream completes, an optional finisher transforms the accumulated result. The sink
materializes into a @scala[`Future`]@java[`CompletionStage`] holding the final result. All processing
happens sequentially on the stream's materialized execution context.

Since each materialization of the sink must start with a fresh accumulator, the factory is invoked once per
materialization. Use @ref:[javaCollectorParallelUnordered](javaCollectorParallelUnordered.md) if parallel
collection is needed.

@ref:[`Sink.collect`](../Sink/collect.md) provides a simpler API for common collection scenarios.

## Example

In this example, `StreamConverters.javaCollector` uses ``Collectors.toList`` to gather stream elements
into a ``List``.

Scala
:   @@snip [JavaCollectorDocExample.scala](/docs/src/test/scala/docs/stream/operators/JavaCollectorDocExample.scala) { #javaCollector }

Java
:   @@snip [JavaCollectorDocExamples.java](/docs/src/test/java/jdocs/stream/operators/JavaCollectorDocExamples.java) { #javaCollector }
