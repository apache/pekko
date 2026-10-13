# aggregateWithBoundary

Aggregate and emit until custom boundary condition met.

@ref[Backpressure aware operators](../index.md#backpressure-aware-operators)

@ref[Timer driven operators](../index.md#timer-driven-operators)

## Signature

@apidoc[Source.aggregateWithBoundary](Source) { scala="#aggregateWithBoundary[Agg,Emit](allocate:()=%3EAgg)(aggregate:(Agg,Out)=%3E(Agg,Boolean),harvest:Agg=%3EEmit,emitOnTimer:Option[(Agg=%3EBoolean,scala.concurrent.duration.FiniteDuration)]):FlowOps.this.Repr[Emit]" java="#aggregateWithBoundary(org.apache.pekko.japi.function.Creator,org.apache.pekko.japi.function.Function2,org.apache.pekko.japi.function.Function,org.apache.pekko.japi.Pair)"}
@apidoc[Flow.aggregateWithBoundary](Flow) { scala="#aggregateWithBoundary[Agg,Emit](allocate:()=%3EAgg)(aggregate:(Agg,Out)=%3E(Agg,Boolean),harvest:Agg=%3EEmit,emitOnTimer:Option[(Agg=%3EBoolean,scala.concurrent.duration.FiniteDuration)]):FlowOps.this.Repr[Emit]" java="#aggregateWithBoundary(org.apache.pekko.japi.function.Creator,org.apache.pekko.japi.function.Function2,org.apache.pekko.japi.function.Function,org.apache.pekko.japi.Pair)" }


## Description

This operator can be customized into a broad class of aggregate/group/fold operators, based on custom state or timer conditions.

This operator adheres to the `ActorAttributes.SupervisionStrategy` attribute for exceptions thrown by `allocate`,
`aggregate`, `harvest`, or timer predicate functions. On `Supervision.Stop` the stream fails; on
`Supervision.Resume` and `Supervision.Restart` the failing element or aggregate is dropped and the stream continues.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the aggregation function decides the aggregate is complete or the timer function returns true

**backpressures** when downstream backpressures and the aggregate is complete

**completes** when upstream completes and the last aggregate has been emitted downstream

**cancels** when downstream cancels

@@@