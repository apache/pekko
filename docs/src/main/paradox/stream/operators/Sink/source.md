# Sink.source

A `Sink` that materializes this `Sink` itself as a `Source`, the returning `Source` can only have one subscriber.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.source](Sink$) { java="#source()" }
@apidoc[Sink.source](Sink$) { scala="#source()" }


## Description

A `Sink` that materialize this `Sink` itself as a `Source`, the returning `Source` can only have one subscriber.

## Reactive Streams semantics

@@@div { .callout }

**cancels** When the materialized `Source` is cancelled or timeout with subscription.

**backpressures** When the materialized `Source` backpressures or not ready to receive elements.

@@@


