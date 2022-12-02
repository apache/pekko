# Sink.onComplete

Invoke a callback when the stream has completed or failed.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.onComplete](Sink$) { scala="#onComplete[T](callback:scala.util.Try[org.apache.pekko.Done]=&gt;Unit):org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.NotUsed]" java="#onComplete(org.apache.pekko.japi.function.Procedure)" }


## Description

Invoke a callback when the stream has completed or failed.

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** never

@@@


