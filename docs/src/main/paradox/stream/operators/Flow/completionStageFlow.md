# Flow.completionStageFlow

Streams the elements through the given future flow once it successfully completes.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Flow.completionStageFlow](Flow$) { java="#completionStageFlow(java.util.concurrent.CompletionStage)" }


## Description

Streams the elements through the given flow once the `CompletionStage` successfully completes. 
If the future fails the stream fails.

## Examples

A deferred creation of the stream based on the initial element by combining `completionStageFlow`
with `prefixAndTail` like so:

Scala
:   @@snip [FutureFlow.java](/docs/src/test/java/jdocs/stream/operators/flow/FutureFlow.java) { #base-on-first-element }


## Reactive Streams semantics

@@@div { .callout }

**emits** when the internal flow is successfully created and it emits

**backpressures** when the internal flow is successfully created and it backpressures

**completes** when upstream completes and all elements have been emitted from the internal flow

**cancels** when downstream cancels (keep reading)
    The operator's default behavior in case of downstream cancellation before nested flow materialization (`CompletionStage` completion) is to cancel immediately.
     This behavior can be controlled by setting the [[org.apache.pekko.stream.Attributes.NestedMaterializationCancellationPolicy.PropagateToNested]] attribute,
    this will delay downstream cancellation until nested flow's materialization which is then immediately cancelled (with the original cancellation cause).
@@@

