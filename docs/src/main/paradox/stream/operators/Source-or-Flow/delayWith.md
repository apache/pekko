# delayWith

Delay every element passed through with a duration that can be controlled dynamically.

@ref[Timer driven operators](../index.md#timer-driven-operators)

## Signature

@apidoc[Source.delayWith](Source) { scala="#delayWith(delayStrategySupplier:()=&gt;org.apache.pekko.stream.scaladsl.DelayStrategy[Out],overFlowStrategy:org.apache.pekko.stream.DelayOverflowStrategy):FlowOps.this.Repr[Out]" java="#delayWith(java.util.function.Supplier,org.apache.pekko.stream.DelayOverflowStrategy)" }
@apidoc[Flow.delayWith](Flow) { scala="#delayWith(delayStrategySupplier:()=&gt;org.apache.pekko.stream.scaladsl.DelayStrategy[Out],overFlowStrategy:org.apache.pekko.stream.DelayOverflowStrategy):FlowOps.this.Repr[Out]" java="#delayWith(java.util.function.Supplier,org.apache.pekko.stream.DelayOverflowStrategy)" }


## Description

Delay every element passed through with a duration that can be controlled dynamically, individually for each elements (via the `DelayStrategy`).


@@@div { .callout }

**emits** there is a pending element in the buffer and configured time for this element elapsed

**backpressures** differs, depends on `OverflowStrategy` set

**completes** when upstream completes and buffered elements has been drained


@@@

