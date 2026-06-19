# Source.queue

Materialize a @apidoc[BoundedSourceQueue] onto which elements can be pushed for emitting from the source.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.queue](Source$) { scala="#queue[T](bufferSize:Int):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.stream.BoundedSourceQueue[T]]" java="#queue(int)" }

## Description

`Source.queue[T](bufferSize)` materializes a @apidoc[BoundedSourceQueue] that gives immediate, synchronous feedback on whether an element was accepted. When the buffer is full the newest element is dropped and `offer` returns @apidoc[QueueOfferResult.Dropped$]. This is the recommended path for bridging an imperative producer with a Pekko Stream.

The @apidoc[BoundedSourceQueue] contains a buffer that can be used by many producers on different threads. When the buffer is full, it will not accept more elements. The return value of `BoundedSourceQueue.offer` is a @apidoc[QueueOfferResult] (not a @scala[`Future`]@java[`CompletionStage`]), which is important in overload scenarios: delivering acknowledgements asynchronously can itself become a source of out-of-memory errors when elements arrive faster than the feedback can be processed.

If you need **backpressure** towards the producer (rather than dropping), prefer one of:

* @apidoc[Source.actorRefWithBackpressure] — for a single, imperative producer that can wait for ack messages.
* @apidoc[MergeHub.source$] — for multiple producers that should be backpressured individually.

@@@ warning { title="Deprecation notice" }

The `Source.queue` overloads that accept an @apidoc[OverflowStrategy] (and materialize a @apidoc[SourceQueueWithComplete]) are **deprecated since 2.0.0**. Their asynchronous `offer` @scala[`Future`]@java[`CompletionStage`] can hang indefinitely under `OverflowStrategy.backpressure` when downstream stalls, which has caused real-world deadlocks. Use `Source.queue[T](bufferSize)` (this page), @apidoc[Source.actorRefWithBackpressure], or @apidoc[MergeHub.source$] instead.

@@@

## Example

Scala
:   @@snip [IntegrationDocSpec.scala](/docs/src/test/scala/docs/stream/IntegrationDocSpec.scala) { #source-queue }

Java
:   @@snip [IntegrationDocTest.java](/docs/src/test/java/jdocs/stream/IntegrationDocTest.java) { #source-queue }

A synchronous example that inspects every `QueueOfferResult` directly:

Scala
:   @@snip [IntegrationDocSpec.scala](/docs/src/test/scala/docs/stream/IntegrationDocSpec.scala) { #source-queue-synchronous }

Java
:   @@snip [IntegrationDocTest.java](/docs/src/test/java/jdocs/stream/IntegrationDocTest.java) { #source-queue-synchronous }

## Migrating from the deprecated `Source.queue(Int, OverflowStrategy)` overloads

| Old call | Replacement |
|----------|-------------|
| `Source.queue(n, OverflowStrategy.dropNew)` | `Source.queue[T](n)` — `BoundedSourceQueue` already drops the newest element. |
| `Source.queue(n, OverflowStrategy.dropHead)` | `Source.queue[T](n)` if drop-new is acceptable. Otherwise build a custom @apidoc[GraphStage] with a FIFO buffer that drops the head. |
| `Source.queue(n, OverflowStrategy.dropTail)` | Same as above; `BoundedSourceQueue` always drops the tail of the pending offer (i.e. the newest). |
| `Source.queue(n, OverflowStrategy.dropBuffer)` | `Source.queue[T](n)` combined with a @apidoc[GraphStage] that clears the buffer on overflow, or rework the producer to tolerate drops. |
| `Source.queue(n, OverflowStrategy.fail)` | `Source.queue[T](n)` and, on `QueueOfferResult.Dropped`, call @apidoc[BoundedSourceQueue.fail] with a `BufferOverflowException`. |
| `Source.queue(n, OverflowStrategy.backpressure)` | @apidoc[Source.actorRefWithBackpressure] (single imperative producer) or @apidoc[MergeHub.source$] (multiple producers). |

`SourceQueueWithComplete.offer` returned a @scala[`Future[QueueOfferResult]`]@java[`CompletionStage<QueueOfferResult>`]; `BoundedSourceQueue.offer` returns `QueueOfferResult` synchronously. Call sites that previously chained `.map`/`.flatMap` on the offer future can usually be rewritten as a direct `match`/`switch` on the result.

## Reactive Streams semantics

@@@div { .callout }

**emits** when there is demand and the queue contains elements

**completes** when downstream completes

@@@
