# gather

Transform each input element into zero or more output elements with a stateful gatherer.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Flow.gather](Flow)

## Description

`gather` creates a new gatherer for each materialization. The gatherer can keep mutable state and emit zero or more
elements for each incoming element by calling `push` on the provided collector.

Unlike `statefulMap` and `statefulMapConcat`, the operator API itself does not require returning tuples or collections
for each input element. This makes `gather` a good fit for allocation-sensitive stateful transformations.

Patterns such as `zipWithIndex`, `bufferUntilChanged`, and `distinctUntilChanged` can be expressed by keeping mutable
state inside the gatherer and pushing outputs directly, instead of returning a new state/output wrapper for every
element.

Compared with `statefulMap`, `gather` covers the same common stateful streaming patterns used in this PR's test suite:
happy-path stateful mapping, delayed completion output, restart/stop supervision behavior, and backpressure-sensitive
one-output transformations. The main difference is that `statefulMap` exposes state as an explicit return value,
including `null` state transitions, while `gather` keeps state inside the gatherer instance itself. Because of that,
`statefulMap` tests about `null` state do not translate one-to-one; the equivalent `gather` coverage focuses on the
observable stream behavior instead.

When the stage terminates or restarts, the gatherer's `onComplete` callback is invoked. Elements pushed from
`onComplete` are emitted before upstream-failure propagation, normal completion, or supervision restart, and are
ignored on downstream cancellation or abrupt termination.

The `gather` operator adheres to the ActorAttributes.SupervisionStrategy attribute.

For one-to-one stateful mapping see @ref:[statefulMap](statefulMap.md). For iterable-based fan-out see
@ref:[statefulMapConcat](statefulMapConcat.md).

## Examples

In the first example, we implement a `zipWithIndex`-like transformation.

Scala
:  @@snip [Gather.scala](/docs/src/test/scala/docs/stream/operators/flow/Gather.scala) { #zipWithIndex }

Java
:   @@snip [Gather.java](/docs/src/test/java/jdocs/stream/operators/flow/Gather.java) { #zipWithIndex }

In the second example, we group incoming elements in batches of three and emit the trailing batch from `onComplete`.

Scala
:  @@snip [Gather.scala](/docs/src/test/scala/docs/stream/operators/flow/Gather.scala) { #grouped }

Java
:   @@snip [Gather.java](/docs/src/test/java/jdocs/stream/operators/flow/Gather.java) { #grouped }

## Reactive Streams semantics

@@@div { .callout }

**emits** the gatherer emits an element and downstream is ready to consume it

**backpressures** downstream backpressures

**completes** upstream completes and all gathered elements have been emitted

**cancels** downstream cancels

@@@
