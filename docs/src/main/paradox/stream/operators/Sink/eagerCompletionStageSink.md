# Sink.eagerCompletionStageSink

Materializes the inner sink when the future completes, even if no elements have arrived yet.

@ref[Sink operators](../index.md#sink-operators)


## Description

Turn a `CompletionStage<Sink>` into a Sink that will consume the values of the source when the future completes
successfully. If the `CompletionStage` is completed with a failure the stream is failed.

Unlike @ref:[completionStageSink](completionStageSink.md) and @ref:[lazyCompletionStageSink](lazyCompletionStageSink.md), this operator materializes the inner sink as soon as the future
completes, even if no elements have arrived yet. This means empty streams complete normally rather than failing
with `NeverMaterializedException`. Elements that arrive before the future completes are buffered.

The materialized future value is completed with the materialized value of the future sink or failed if the
future fails, upstream fails, or downstream cancels before the inner sink is materialized.

See also @ref:[completionStageSink](completionStageSink.md), @ref:[lazyCompletionStageSink](lazyCompletionStageSink.md).

## Reactive Streams semantics

@@@div { .callout }

**cancels** if the future fails or if the created sink cancels 

**backpressures** when initialized and when created sink backpressures

@@@

