# wireTap

Attaches the given `Sink` to this `Flow` as a wire tap, meaning that elements that pass through will also be sent to the wire-tap `Sink`, without the latter affecting the mainline flow.

@ref[Fan-out operators](../index.md#fan-out-operators)

## Signature

@apidoc[Source.wireTap](Source) { scala="#wireTap(f:Out=&gt;Unit):FlowOps.this.Repr[Out]" java="#wireTap(org.apache.pekko.japi.function.Procedure)" }
@apidoc[Flow.wireTap](Flow) { scala="#wireTap(f:Out=&gt;Unit):FlowOps.this.Repr[Out]" java="#wireTap(org.apache.pekko.japi.function.Procedure)" }
@apidoc[Source.wireTap](Source) { scala="#wireTap(that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],_]):FlowOps.this.Repr[Out]" java="#wireTap(org.apache.pekko.stream.Graph)" }
@apidoc[Flow.wireTap](Flow) { scala="#wireTap(that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SinkShape[Out],_]):FlowOps.this.Repr[Out]" java="#wireTap(org.apache.pekko.stream.Graph)" }


## Description

Attaches the given `Sink` to this `Flow` as a wire tap, meaning that elements that pass
through will also be sent to the wire-tap `Sink`, without the latter affecting the mainline flow.
If the wire-tap `Sink` backpressures, elements that would've been sent to it will be dropped instead.

## Reactive Streams semantics

@@@div { .callout }

**emits** element is available and demand exists from the downstream; the element will
also be sent to the wire-tap `Sink` if there is demand.

**backpressures** downstream backpressures

**completes** upstream completes

**cancels** downstream cancels

@@@

