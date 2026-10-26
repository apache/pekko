# mergeLatest

Merge multiple sources.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Flow.mergeLatest](Flow) { scala="#mergeLatest[U%3E:Out,M](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],M],eagerComplete:Boolean):FlowOps.this.Repr[Seq[U]]" java="#mergeLatest(org.apache.pekko.stream.Graph,boolean)" } 

## Description

MergeLatest joins elements from N input streams into stream of lists of size N.
The i-th element in the list is the latest emitted element from the i-th input stream.
It starts emitting after every input has emitted at least once. By default, when downstream is
back-pressured, MergeLatest continues consuming inputs and retains only their latest values; changes
that are superseded before demand returns are conflated. This bounds MergeLatest's own retained update
state, but does not bound buffers in upstream stages. Configure
`pekko.stream.materializer.merge-latest.mode = legacy` to retain every update, as in the previous behavior.
If `eagerComplete` is set to true then it completes as soon as the first upstream completes; otherwise,
it completes when all upstreams complete. A pending latest combination is emitted before completion
when downstream demand becomes available.

## Example

This example takes a stream of prices and quantities and emits the price multiplied by the quantity each time
the price or the quantity changes:

Scala
:   @@snip [MergeLatest.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/MergeLatest.scala) { #mergeLatest }
 
Java
:   @@snip [MergeLatest.java](/docs/src/test/java/jdocs/stream/operators/sourceorflow/MergeLatest.java) { #mergeLatest }
 

## Reactive Streams semantics

@@@div { .callout }

**emits** when an input element is available and each input has emitted at least once; while downstream is back-pressured, the default mode retains only the latest value from each input

**backpressures** never in the default `latest` mode; it continues pulling inputs and conflates superseded combinations. The `legacy` mode buffers every update.

**completes** when all upstreams complete (`eagerComplete=false`) or one upstream completes (`eagerComplete=true`); a pending latest combination is emitted first when demand is available
@@@

