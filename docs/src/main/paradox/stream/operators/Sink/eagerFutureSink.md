# Sink.eagerFutureSink

Materializes the inner sink when the future completes, even if no elements have arrived yet.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.eagerFutureSink](Sink$) { scala="#eagerFutureSink[T,M](future:scala.concurrent.Future[org.apache.pekko.stream.scaladsl.Sink[T,M]]):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[M]]" }


## Description

Turn a `Future[Sink]` into a Sink that will consume the values of the source when the future completes
successfully. If the `Future` is completed with a failure the stream is failed.

Unlike @ref:[futureSink](futureSink.md) and @ref:[lazyFutureSink](lazyFutureSink.md), this operator materializes the inner sink as soon as the future
completes, even if no elements have arrived yet. This means empty streams complete normally rather than failing
with `NeverMaterializedException`. Elements that arrive before the future completes are buffered.

The materialized future value is completed with the materialized value of the future sink or failed if the
future fails, upstream fails, or downstream cancels before the inner sink is materialized.

See also @ref:[futureSink](futureSink.md), @ref:[lazyFutureSink](lazyFutureSink.md).

## Reactive Streams semantics

@@@div { .callout }

**cancels** if the future fails or if the created sink cancels 

**backpressures** when initialized and when created sink backpressures

@@@

