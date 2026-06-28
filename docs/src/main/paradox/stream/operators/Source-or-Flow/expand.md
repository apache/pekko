# expand

Like `extrapolate`, but does not have the `initial` argument, and the `Iterator` is also used in lieu of the original element, allowing for it to be rewritten and/or filtered.

@ref[Backpressure aware operators](../index.md#backpressure-aware-operators)

## Signature

@apidoc[Source.expand](Source) { scala="#expand[U](expander:Out=&gt;Iterator[U]):FlowOps.this.Repr[U]" java="#expand(org.apache.pekko.japi.function.Function)" }
@apidoc[Flow.expand](Flow) { scala="#expand[U](expander:Out=&gt;Iterator[U]):FlowOps.this.Repr[U]" java="#expand(org.apache.pekko.japi.function.Function)" }

## Description

Like `extrapolate`, but does not have the `initial` argument, and the `Iterator` is also used in lieu of the original 
element, allowing for it to be rewritten and/or filtered.

See @ref:[Understanding extrapolate and expand](../../stream-rate.md#understanding-extrapolate-and-expand) for more information
and examples.

This operator adheres to the `ActorAttributes.SupervisionStrategy` attribute for exceptions thrown by the `expander`
function or during iterator evaluation (`hasNext`/`next`). On `Supervision.Stop` the stream fails; on
`Supervision.Resume` the failed element is dropped and the current extrapolation state is kept when the failure
occurred in the `expander` function (a previously active iterator is retained), but is necessarily discarded when
the failure occurred during iterator evaluation; on `Supervision.Restart` the failed element is dropped and the
current extrapolation state is reset.

## Example

Imagine a streaming client decoding a video. It is possible the network bandwidth is a bit 
unreliable. It's fine, as long as the audio remains fluent, it doesn't matter if we can't decode 
a frame or two (or more). But we also want to watermark every decoded frame with the name of 
our colleague. `expand` provides access to the element flowing through the stream
and let's us create extra frames in case the producer slows down:

Scala
:   @@snip [ExtrapolateAndExpand.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/ExtrapolateAndExpand.scala) { #expand }

Java
:   @@snip [ExtrapolateAndExpand.java](/docs/src/test/java/jdocs/stream/operators/sourceorflow/ExtrapolateAndExpand.java) { #expand }


## Reactive Streams semantics

@@@div { .callout }

**emits** when downstream stops backpressuring

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
