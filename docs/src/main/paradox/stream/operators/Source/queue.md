# Source.queue

Materialize a `BoundedSourceQueue` or `SourceQueue` onto which elements can be pushed for emitting from the source.

@ref[Source operators](../index.md#source-operators)

@@@ warning { title="Deprecation notice (since 2.0.0)" }

The `Source.queue` overloads that accept an @apidoc[OverflowStrategy] (and materialize a @apidoc[SourceQueueWithComplete]) are **deprecated**. Their asynchronous `offer` @scala[`Future`]@java[`CompletionStage`] can hang indefinitely under `OverflowStrategy.backpressure` when downstream stalls, which has caused real-world deadlocks.

Prefer `Source.queue[T](bufferSize)` (this page), which materializes a @apidoc[BoundedSourceQueue] with synchronous feedback and drop-newest overflow. For backpressure towards the producer, use @apidoc[Source.actorRefWithBackpressure] (single imperative producer) or @apidoc[MergeHub.source$] (multiple producers). See the [migration table](#migrating-from-the-deprecated-sourcequeueint-overflowstrategy-overloads) below for a per-strategy replacement.

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

**completes** when downstream completes

@@@

## Migrating from the deprecated `Source.queue(Int, OverflowStrategy)` overloads

| Old call | Replacement |
|----------|-------------|
| `Source.queue(n, OverflowStrategy.dropNew)` | `Source.queue[T](n)` — `BoundedSourceQueue` already drops the newest element. |
| `Source.queue(n, OverflowStrategy.dropHead)` | `Source.queue[T](n)` if drop-new is acceptable. Otherwise build a custom @apidoc[GraphStage] with a FIFO buffer that drops the head. |
| `Source.queue(n, OverflowStrategy.dropTail)` | Same as above; `BoundedSourceQueue` always drops the newest offer (i.e. the tail). |
| `Source.queue(n, OverflowStrategy.dropBuffer)` | `Source.queue[T](n)` combined with a @apidoc[GraphStage] that clears the buffer on overflow, or rework the producer to tolerate drops. |
| `Source.queue(n, OverflowStrategy.fail)` | `Source.queue[T](n)` and, on `QueueOfferResult.Dropped`, call @apidoc[BoundedSourceQueue.fail] with a `BufferOverflowException`. |
| `Source.queue(n, OverflowStrategy.backpressure)` | @apidoc[Source.actorRefWithBackpressure] (single imperative producer) or @apidoc[MergeHub.source$] (multiple producers). |

`SourceQueueWithComplete.offer` returned a @scala[`Future[QueueOfferResult]`]@java[`CompletionStage<QueueOfferResult>`]; `BoundedSourceQueue.offer` returns `QueueOfferResult` synchronously. Call sites that previously chained `.map`/`.flatMap` on the offer future can usually be rewritten as a direct `match`/`switch` on the result.
