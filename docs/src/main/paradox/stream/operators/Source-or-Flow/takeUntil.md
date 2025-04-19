# takeUntil

Pass elements downstream until the predicate function returns true. The first element for which the predicate returns true is also emitted before the stream completes.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.takeUntil](Source) { scala="#takeUntil(p:Out=&gt;Boolean):FlowOps.this.Repr[Out]" java="#takeUntil(
org.apache.pekko.japi.function.Predicate)" }
@apidoc[Flow.takeUntil](Flow) { scala="#takeUntil(p:Out=&gt;Boolean):FlowOps.this.Repr[Out]" java="#takeUntil(
org.apache.pekko.japi.function.Predicate)" }

## Description

Pass elements downstream until the predicate function returns true. The first element for which the predicate returns true is also emitted before the stream completes.

## Reactive Streams semantics

@@@div { .callout }

**emits** the predicate is false or the first time the predicate is true

**backpressures** when downstream backpressures

**completes** after predicate returned true or upstream completes

@@@
