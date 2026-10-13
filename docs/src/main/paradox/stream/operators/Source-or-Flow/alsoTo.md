# alsoTo

Attaches the given `Sink` to this `Flow`, meaning that elements that pass through this `Flow` will also be sent to the `Sink`.

@ref[Fan-out operators](../index.md#fan-out-operators)

## Signature

@apidoc[Source.alsoTo](Source) { scala="#alsoTo(that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],_]):FlowOps.this.Repr[Out]" java="#alsoTo(org.apache.pekko.stream.Graph)" }
@apidoc[Flow.alsoTo](Flow) { scala="#alsoTo(that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],_]):FlowOps.this.Repr[Out]" java="#alsoTo(org.apache.pekko.stream.Graph)" }


## Description

Attaches the given `Sink` to this `Flow`, meaning that elements that pass through this `Flow` will also be sent to the `Sink`.

By default, cancellation or failure of the attached `Sink` cancels the main stream. The `alsoTo` overload with
`propagateCancellation = false` can be used when the attached `Sink` is a best-effort side sink, such as logging or
metrics, and its cancellation or failure should not terminate the main stream. In that mode, the operator still
backpressures when the side `Sink` is active and backpressuring, but once the side `Sink` cancels or fails, elements
continue to the main downstream only.

## Reactive Streams semantics

@@@div { .callout }

**emits** when an element is available and demand exists both from the `Sink` and the downstream

**backpressures** when downstream or `Sink` backpressures

**completes** when upstream completes

**cancels** when downstream cancels. With the default cancellation propagation, the operator also cancels when the
attached `Sink` cancels or fails. With `propagateCancellation = false`, cancellation or failure of the attached `Sink`
does not cancel the main stream.

@@@

