# Sink.never

Always backpressure never cancel and never consume any elements from the stream.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.never](Sink$) { java="#never()" }
@apidoc[Sink.never](Sink$) { scala="#never:org.apache.pekko.stream.scaladsl.Sink[Any,scala.concurrent.Future[org.apache.pekko.Done]]" }


## Description

A `Sink` that will always backpressure never cancel and never consume any elements from the stream.

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** always

@@@


