# StreamConverters.javaCollectorParallelUnordered

Create a sink which materializes into a @scala[`Future`] @java[`CompletionStage`] which will be completed with a result of the Java `Collector` transformation and reduction operations.

@ref[Additional Sink and Source converters](../index.md#additional-sink-and-source-converters)

## Signature

@apidoc[StreamConverters.javaCollectorParallelUnordered](StreamConverters$) { scala="#javaCollectorParallelUnordered[T,R](parallelism:Int)(collectorFactory:()=&gt;java.util.stream.Collector[T,_,R]):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[R]]" java="#javaCollectorParallelUnordered(int,org.apache.pekko.japi.function.Creator)" }


## Description

`javaCollectorParallelUnordered` creates a @apidoc[Sink] that accepts a factory for a Java @javadoc[java.util.stream.Collector](java.util.stream.Collector)
and processes incoming elements in parallel. The elements are distributed over `parallelism` workers using a
@ref:[Balance](../Balance.md), so there is no guarantee about which worker receives which element, and the
relative order of the elements is not preserved. Each worker runs asynchronously and accumulates the elements it
receives into its own mutable result container created by the `Collector`. After the stream completes, the
partial results are merged using the `Collector`'s combiner and transformed by its finisher. The sink materializes
into a @scala[`Future`]@java[`CompletionStage`] holding the final result.

Because the order of the elements is not preserved, only use this with a `Collector` whose result does not depend
on the order in which elements are accumulated and combined. If `parallelism` is 1 this behaves exactly like
@ref:[javaCollector](javaCollector.md).

Since a stream can be materialized multiple times, the function producing the `Collector` must be able to handle
multiple invocations.
