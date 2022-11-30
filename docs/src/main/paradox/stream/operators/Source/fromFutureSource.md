# Source.fromFutureSource

Deprecated by @ref:[`Source.futureSource`](futureSource.md).

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.fromFutureSource](Source$) { scala="#fromFutureSource[T,M](future:scala.concurrent.Future[org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[T],M]]):org.apache.pekko.stream.scaladsl.Source[T,scala.concurrent.Future[M]]" }


## Description

`fromFutureSource` has been deprecated in 2.6.0, use @ref:[futureSource](futureSource.md) instead.

Streams the elements of the given future source once it successfully completes.
If the future fails the stream is failed.

## Reactive Streams semantics

@@@div { .callout }

**emits** the next value from the *future* source, once it has completed

**completes** after the *future* source completes

@@@

