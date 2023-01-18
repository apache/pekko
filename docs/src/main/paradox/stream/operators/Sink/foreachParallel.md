# Sink.foreachParallel

Like `foreach` but allows up to `parallellism` procedure calls to happen in parallel.

@ref[Sink operators](../index.md#sink-operators)

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** when the previous parallel procedure invocations has not yet completed

@@@

