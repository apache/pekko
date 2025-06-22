# groupedAdjacentBy

Partitions this stream into chunks by a delimiter function.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.groupedAdjacentBy](Source) { scala="#groupedAdjacentBy(f:Out=&gt;T):FlowOps.this.Repr[scala.collection.immutable.Seq[Out]]" java="#groupedAdjacentBy(org.apache.pekko.japi.function.Function)" }
@apidoc[Flow.groupedAdjacentBy](Flow) { scala="#groupedAdjacentBy(f:Out=&gt;T):FlowOps.this.Repr[scala.collection.immutable.Seq[Out]]" java="#groupedAdjacentBy(org.apache.pekko.japi.function.Function)" }


## Description

Partitions this stream into chunks by a delimiter function.

See also:

* @ref[groupedAdjacentByWeighted](groupedAdjacentByWeighted.md) for a variant that groups with weight limit too.

## Examples

The example below demonstrates how `groupedAdjacentBy` partitions the elements into @scala[`Seq`] @java[`List`].

Scala
:  @@snip [GroupedAdjacentBy.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/GroupedAdjacentBy.scala) { #groupedAdjacentBy }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #groupedAdjacentBy }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the delimiter function returns a different value than the previous element's result

**backpressures** when a chunk has been assembled and downstream backpressures

**completes** when upstream completes

@@@


