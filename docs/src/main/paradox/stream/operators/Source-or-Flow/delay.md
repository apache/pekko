# delay

Delay every element passed through with a specific duration.

@ref[Timer driven operators](../index.md#timer-driven-operators)

## Signature

@apidoc[Source.delay](Source) { scala="#delay(of:scala.concurrent.duration.FiniteDuration,strategy:org.apache.pekko.stream.DelayOverflowStrategy):FlowOps.this.Repr[Out]" java="#delay(java.time.Duration,org.apache.pekko.stream.DelayOverflowStrategy)" }
@apidoc[Flow.delay](Flow) { scala="#delay(of:scala.concurrent.duration.FiniteDuration,strategy:org.apache.pekko.stream.DelayOverflowStrategy):FlowOps.this.Repr[Out]" java="#delay(java.time.Duration,org.apache.pekko.stream.DelayOverflowStrategy)" }


## Description

Delay every element passed through with a specific duration.

## Reactive Streams semantics

@@@div { .callout }

**emits** there is a pending element in the buffer and configured time for this element elapsed

**backpressures** differs, depends on `OverflowStrategy` set

**completes** when upstream completes and buffered elements has been drained


@@@

