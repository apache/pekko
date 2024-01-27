# flattenMerge

Flattens a stream of Source into a single output stream by merging.

@ref[Nesting and flattening operators](../index.md#nesting-and-flattening-operators)

## Signature

@apidoc[Flow.flattenMerge](Flow) { scala="#flattenMerge%5BT%2C%20M%5D(breadth%3A%20Int)(implicit%20ev%3A%20Out%20%3C%3A%3C%20Graph%5BSourceShape%5BT%5D%2C%20M%5D):FlowOps.this.Repr[T]" } 

## Description

Flattens a stream of `Source` into a single output stream by merging, where at most breadth substreams are being consumed 
at any given time. This function is equivalent to `flatMapMerge(breadth, identity)`.
Emits when a currently consumed substream has an element available

## Reactive Streams semantics

@@@div { .callout }

**emits** when one of the currently consumed substreams has an element available

**backpressures** when downstream backpressures or the max number of substreams is reached

**completes** when upstream completes and all consumed substreams complete

@@@


