# prefixAndTail

Take up to *n* elements from the stream (less than *n* only if the upstream completes before emitting *n* elements) and returns a pair containing a strict sequence of the taken element and a stream representing the remaining elements.

@ref[Nesting and flattening operators](../index.md#nesting-and-flattening-operators)

## Signature

@apidoc[Source.prefixAndTail](Source) { scala="#prefixAndTail[U&gt;:Out](n:Int):FlowOps.this.Repr[(scala.collection.immutable.Seq[Out],org.apache.pekko.stream.scaladsl.Source[U,org.apache.pekko.NotUsed])]" java="#prefixAndTail(int)" }
@apidoc[Flow.prefixAndTail](Flow) { scala="#prefixAndTail[U&gt;:Out](n:Int):FlowOps.this.Repr[(scala.collection.immutable.Seq[Out],org.apache.pekko.stream.scaladsl.Source[U,org.apache.pekko.NotUsed])]" java="#prefixAndTail(int)" }


## Description

Take up to *n* elements from the stream (less than *n* only if the upstream completes before emitting *n* elements)
and returns a pair containing a strict sequence of the taken element and a stream representing the remaining elements.

After the prefix has been emitted, `prefixAndTail` issues a single speculative pull on its upstream so that an empty
tail (the upstream completes before producing any further element) is detected immediately. If the emitted tail
`Source` is intentionally discarded and the substream happens to be empty, the stage completes without waiting for
the configured `pekko.stream.materializer.subscription-timeout`.

If the speculative pull delivers an element before the tail `Source` is materialized, that element is buffered and
forwarded as the first element to whoever materializes the tail. Tails that are discarded by downstream while
upstream still has elements continue to be governed by the configured subscription timeout.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the configured number of prefix elements are available. Emits this prefix, and the rest as a substream

**backpressures** when downstream backpressures or substream backpressures

**completes** when prefix elements has been consumed and substream has been consumed

@@@


