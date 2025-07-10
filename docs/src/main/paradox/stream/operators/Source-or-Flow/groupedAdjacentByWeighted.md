# groupedAdjacentByWeighted

Partitions this stream into chunks by a delimiter function and a weight limit.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.groupedAdjacentByWeighted](Source) { scala="#groupedAdjacentByWeighted(f:Out=&gt;T,maxWeight:Long)(costFn:Out=&gt;Long):FlowOps.this.Repr[scala.collection.immutable.Seq[Out]]" java="#groupedAdjacentBy(org.apache.pekko.japi.function.Function,long,org.apache.pekko.japi.function.Function)" }
@apidoc[Flow.groupedAdjacentByWeighted](Flow) { scala="#groupedAdjacentByWeighted(f:Out=&gt;T,maxWeight:Long)(costFn:Out=&gt;Long):FlowOps.this.Repr[scala.collection.immutable.Seq[Out]]" java="#groupedAdjacentBy(org.apache.pekko.japi.function.Function,long,org.apache.pekko.japi.function.Function)" }

## Description

Partitions this stream into chunks by a delimiter function.

See also:

* @ref[groupedAdjacentBy](groupedAdjacentBy.md) for a simpler variant.

## Examples

The example below demonstrates how `groupedAdjacentByWeighted` partitions the elements into @scala[`Seq`] @java[`List`].

Scala
:  @@snip [GroupedAdjacentBy.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/GroupedAdjacentBy.scala) { #groupedAdjacentByWeighted }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #groupedAdjacentByWeighted }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the delimiter function returns a different value than the previous element's result,  or exceeds the `maxWeight`.

**backpressures** when a chunk has been assembled and downstream backpressures

**completes** when upstream completes

@@@


