# switchMap

Transforms each input element into a `Source` of output elements that is then flattened into the output stream until a new input element is received (at which point the current (now previous) substream is cancelled and the new one is flattend into the output stream).

@ref[Nesting and flattening operators](../index.md#nesting-and-flattening-operators)

## Signature

@apidoc[Flow.switchMap](Flow) { scala="#switchMap[T,M](f:Out=%3Eorg.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[T],M]):FlowOps.this.Repr[T]" java="#switchMap(org.apache.pekko.japi.function.Function)" } 

## Description

Transforms each input element into a `Source` of output elements that is then flattened into the output stream until a 
new input element is received at which point the current (now previous) substream is cancelled while the `Source` resulting
from the input element is subscribed to and flattened into the output stream. So effectively, only the "latest" `Source`
is flattened into the output stream (which is why this  operator is sometimes also called "flatMapLatest").

## Reactive Streams semantics

@@@div { .callout }

**emits** when the current substream has an element available

**backpressures** never

**completes** upstream completes and the current substream completes

@@@


