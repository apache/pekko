# Source.queue

Materialize a `BoundedSourceQueue` or `SourceQueue` onto which elements can be pushed for emitting from the source.

@ref[Source operators](../index.md#source-operators)

@@@ warning { title="Deprecation notice (since 2.0.0)" }

The `Source.queue` overloads that accept an @apidoc[OverflowStrategy] and materialize a `SourceQueueWithComplete` are **deprecated**. Their asynchronous `offer` @scala[`Future`]@java[`CompletionStage`] can hang indefinitely under `OverflowStrategy.backpressure` when downstream stalls, which has caused real-world deadlocks.

Prefer `Source.queue[T](bufferSize)` (this page), which materializes a @apidoc[BoundedSourceQueue] with synchronous feedback and drop-newest overflow. For backpressure towards the producer, use @ref:[`Source.actorRefWithBackpressure`](actorRefWithBackpressure.md) (single imperative producer) or `MergeHub.source` (multiple producers). See the [migration table](#migrating-from-the-deprecated-sourcequeueint-overflowstrategy-overloads) below for a per-strategy replacement.

@@@

## Signature (`BoundedSourceQueue`)

@apidoc[Source.queue](Source$) { scala="#queue[T](bufferSize:Int):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.stream.scaladsl.BoundedSourceQueue[T]]" java="#queue(int)" }

## Description (`BoundedSourceQueue`)

The `BoundedSourceQueue` is an optimized variant of the `SourceQueue` which will drop the newest elements when back pressuring and buffer is full. 
The `BoundedSourceQueue` will give immediate, synchronous feedback whether an element was accepted or not and is therefore recommended for situations where overload and dropping elements is expected and needs to be handled quickly.

In contrast, the `SourceQueue` offers more variety of `OverflowStrategies` but feedback is only asynchronously provided through a @scala[`Future`]@java[`CompletionStage`] value. 
In cases where elements need to be discarded quickly at times of overload to avoid out-of-memory situations, delivering feedback asynchronously can itself become a problem. 
This happens if elements come in faster than the feedback can be delivered in which case the feedback mechanism itself is part of the reason that an out-of-memory situation arises.

In summary, prefer `BoundedSourceQueue` over `SourceQueue` especially in high-load scenarios. 
Use `SourceQueue` if you need one of the other `OverflowStrategies`.

The `BoundedSourceQueue` contains a buffer that can be used by many producers on different threads.
When the buffer is full, the `BoundedSourceQueue` will not accept more elements.
The return value of `BoundedSourceQueue.offer()` immediately returns a `QueueOfferResult` (as opposed to an asynchronous value returned by `SourceQueue`).
A synchronous result is important in order to avoid situations where offer acknowledgements are handled slower than the rate of which elements are offered, which will eventually lead to an Out Of Memory error.

## Example (`BoundedSourceQueue`)

Scala
:   @@snip [IntegrationDocSpec.scala](/docs/src/test/scala/docs/stream/IntegrationDocSpec.scala) { #source-queue-synchronous }

Java
:   @@snip [IntegrationDocTest.java](/docs/src/test/java/jdocs/stream/IntegrationDocTest.java) { #source-queue-synchronous }

## Signature (`SourceQueue`)

@@@ warning

These two overloads are deprecated since 2.0.0. See the [migration table](#migrating-from-the-deprecated-sourcequeueint-overflowstrategy-overloads) below.

@@@

@apidoc[Source.queue](Source$) { scala="#queue[T](bufferSize:Int,overflowStrategy:org.apache.pekko.stream.OverflowStrategy):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.stream.scaladsl.SourceQueueWithComplete[T]]" java="#queue(int,org.apache.pekko.stream.OverflowStrategy)" }
@apidoc[Source.queue](Source$) { scala="#queue[T](bufferSize:Int,overflowStrategy:org.apache.pekko.stream.OverflowStrategy,maxConcurrentOffers:Int):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.stream.scaladsl.SourceQueueWithComplete[T]]" java="#queue(int,org.apache.pekko.stream.OverflowStrategy,int)" }

## Description (`SourceQueue`)

Materialize a `SourceQueue` onto which elements can be pushed for emitting from the source. The queue contains
a buffer, if elements are pushed onto the queue faster than the source is consumed the overflow will be handled with
a strategy specified by the user. Functionality for tracking when an element has been emitted is available through
`SourceQueue.offer`.

Using `Source.queue` you can push elements to the queue and they will be emitted to the stream if there is
demand from downstream, otherwise they will be buffered until request for demand is received. Elements in the buffer
will be discarded if downstream is terminated.

In combination with the queue, the @ref[`throttle`](./../Source-or-Flow/throttle.md) operator can be used to control the processing to a given limit, e.g. `5 elements` per `3 seconds`.

## Example (`SourceQueue`)

Scala
:   @@snip [IntegrationDocSpec.scala](/docs/src/test/scala/docs/stream/IntegrationDocSpec.scala) { #source-queue }

Java
:   @@snip [IntegrationDocTest.java](/docs/src/test/java/jdocs/stream/IntegrationDocTest.java) { #source-queue }

## Reactive Streams semantics

@@@div { .callout }

**emits** when there is demand and the queue contains elements

**completes** when the queue is completed (via `complete()` on the materialized queue) and all buffered elements have been emitted

@@@

## Migrating from the deprecated `Source.queue(Int, OverflowStrategy)` overloads

| Old call | Replacement |
|----------|-------------|
| `Source.queue(n, OverflowStrategy.dropNew)` (`dropNew` was removed in 2.0.0) | `Source.queue[T](n)`, which has the same semantics: the offered element is dropped when the buffer is full. |
| `Source.queue(n, OverflowStrategy.dropHead)` | `Source.queue[T](n)` if dropping the offered element instead of the oldest buffered one is acceptable. Otherwise build a custom @apidoc[GraphStage] with a FIFO buffer that drops the head. |
| `Source.queue(n, OverflowStrategy.dropTail)` | `Source.queue[T](n)` if dropping the offered element instead of the youngest buffered one is acceptable. `BoundedSourceQueue` leaves the buffer unchanged and reports `QueueOfferResult.Dropped` for the offered element. |
| `Source.queue(n, OverflowStrategy.dropBuffer)` | `Source.queue[T](n)` combined with a @apidoc[GraphStage] that clears the buffer on overflow, or rework the producer to tolerate drops. |
| `Source.queue(n, OverflowStrategy.fail)` | `Source.queue[T](n)` and, on `QueueOfferResult.Dropped`, call `BoundedSourceQueue.fail` with a `BufferOverflowException`. |
| `Source.queue(n, OverflowStrategy.backpressure)` | @ref:[`Source.actorRefWithBackpressure`](actorRefWithBackpressure.md) (single imperative producer) or `MergeHub.source` (multiple producers). |

`SourceQueueWithComplete.offer` returned a @scala[`Future[QueueOfferResult]`]@java[`CompletionStage<QueueOfferResult>`]; `BoundedSourceQueue.offer` returns `QueueOfferResult` synchronously. Call sites that previously chained `.map`/`.flatMap` on the offer future can usually be rewritten as a direct `match`/`switch` on the result.

### Dropping elements (`dropHead`, `dropTail`, `dropBuffer`)

`BoundedSourceQueue` drops the element being offered when the buffer is full and tells the caller synchronously, instead of completing a @scala[`Future`]@java[`CompletionStage`].

Before:

Scala
:   @@snip [QueueMigrationDocSpec.scala](/docs/src/test/scala/docs/stream/operators/source/QueueMigrationDocSpec.scala) { #drop-before }

Java
:   @@snip [QueueMigrationDocTest.java](/docs/src/test/java/jdocs/stream/operators/source/QueueMigrationDocTest.java) { #drop-before }

After:

Scala
:   @@snip [QueueMigrationDocSpec.scala](/docs/src/test/scala/docs/stream/operators/source/QueueMigrationDocSpec.scala) { #drop-after }

Java
:   @@snip [QueueMigrationDocTest.java](/docs/src/test/java/jdocs/stream/operators/source/QueueMigrationDocTest.java) { #drop-after }

### Failing the stream on overflow (`fail`)

`BoundedSourceQueue` reports `QueueOfferResult.Dropped` instead of failing the stream. To keep the old behavior, fail the queue yourself:

Scala
:   @@snip [QueueMigrationDocSpec.scala](/docs/src/test/scala/docs/stream/operators/source/QueueMigrationDocSpec.scala) { #fail-after }

Java
:   @@snip [QueueMigrationDocTest.java](/docs/src/test/java/jdocs/stream/operators/source/QueueMigrationDocTest.java) { #fail-after }

### Backpressuring the producer (`backpressure`)

A typical use of `OverflowStrategy.backpressure` offers the next element only after the previous offer completed:

Scala
:   @@snip [QueueMigrationDocSpec.scala](/docs/src/test/scala/docs/stream/operators/source/QueueMigrationDocSpec.scala) { #backpressure-before }

Java
:   @@snip [QueueMigrationDocTest.java](/docs/src/test/java/jdocs/stream/operators/source/QueueMigrationDocTest.java) { #backpressure-before }

For a single imperative producer, @ref:[`Source.actorRefWithBackpressure`](actorRefWithBackpressure.md) combined with `ask` gives the same "wait for the offer to be accepted" shape. The ack is sent to the sender of each element, so the `ask` @scala[`Future`]@java[`CompletionStage`] completes once the element was emitted. Sending a new element before the previous one was acknowledged fails the stream, just like exceeding `maxConcurrentOffers` failed the offer before. With the typed actor API, use @ref:[`ActorSource.actorRefWithBackpressure`](../ActorSource/actorRefWithBackpressure.md) the same way.

Scala
:   @@snip [QueueMigrationDocSpec.scala](/docs/src/test/scala/docs/stream/operators/source/QueueMigrationDocSpec.scala) { #backpressure-single-producer }

Java
:   @@snip [QueueMigrationDocTest.java](/docs/src/test/java/jdocs/stream/operators/source/QueueMigrationDocTest.java) { #backpressure-single-producer }

For multiple producers, or when `maxConcurrentOffers` was greater than 1, use @ref:[`MergeHub.source`](../../stream-dynamic.md#using-the-mergehub). Each producer becomes a stream connected to the materialized `Sink` and is back-pressured on its own. If the elements already come from a stream (an iterator, a `Future`, another `Source`), connecting that stream directly is usually simpler than offering elements one at a time.

Scala
:   @@snip [QueueMigrationDocSpec.scala](/docs/src/test/scala/docs/stream/operators/source/QueueMigrationDocSpec.scala) { #backpressure-multiple-producers }

Java
:   @@snip [QueueMigrationDocTest.java](/docs/src/test/java/jdocs/stream/operators/source/QueueMigrationDocTest.java) { #backpressure-multiple-producers }
