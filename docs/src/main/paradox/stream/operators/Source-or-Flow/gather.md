# gather

Transform each input element into zero or more output elements using a stateful gatherer.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Flow.gather](Flow) { scala="#gather%5BT%5D%28create%3A%28%29%3D%3EGatherer%5BOut%2CT%5D%29%3ARepr%5BT%5D" java="#gather(org.apache.pekko.japi.function.Creator)" }

## Description

Transform each input element into zero or more output elements without requiring tuple or collection allocations
imposed by the operator API itself.

A new `Gatherer` is created for each materialization and can keep mutable state in fields or closures.
The provided `GatherCollector` can emit zero or more output elements for each input element.

The collector is only valid while the callback is running. Emitted elements MUST NOT be `null`.

The `onComplete` callback is invoked once whenever the stage terminates or restarts: on upstream completion,
upstream failure, downstream cancellation, abrupt stage termination, or supervision restart.
Elements emitted from `onComplete` are emitted before upstream-failure propagation, completion, or restart,
and are ignored on downstream cancellation and abrupt termination.

The `gather` operator adheres to the @ref:[ActorAttributes.SupervisionStrategy](../../actors.md) attribute.

For a simpler stateless mapping, use @ref:[map](map.md) or @ref:[mapConcat](mapConcat.md).

## Examples

In the first example, we implement a `zipWithIndex` operator like @ref:[zipWithIndex](zipWithIndex.md):

Scala
:  @@snip [Gather.scala](/docs/src/test/scala/docs/stream/operators/flow/Gather.scala) { #zipWithIndex }

Java
:   @@snip [Gather.java](/docs/src/test/java/jdocs/stream/operators/flow/Gather.java) { #zipWithIndex }

In the second example, elements are buffered until a different element arrives, then emitted:

Scala
:  @@snip [Gather.scala](/docs/src/test/scala/docs/stream/operators/flow/Gather.scala) { #bufferUntilChanged }

Java
:   @@snip [Gather.java](/docs/src/test/java/jdocs/stream/operators/flow/Gather.java) { #bufferUntilChanged }

In the third example, repeated incoming elements are only emitted once:

Scala
:  @@snip [Gather.scala](/docs/src/test/scala/docs/stream/operators/flow/Gather.scala) { #distinctUntilChanged }

Java
:   @@snip [Gather.java](/docs/src/test/java/jdocs/stream/operators/flow/Gather.java) { #distinctUntilChanged }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the gatherer emits an element and downstream is ready to consume it

**backpressures** when downstream backpressures

**completes** upstream completes and the gatherer has emitted all pending elements, including `onComplete`

**cancels** downstream cancels

@@@
