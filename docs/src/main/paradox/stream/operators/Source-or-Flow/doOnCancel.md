# doOnCancel

Run the given function when the downstream cancels.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.doOnCancel](Source) { scala="#doOnCancel(f:(Throwable,Boolean)=&gt;Unit):FlowOps.this.Repr[Out]" java="#doOnCancel(org.apache.pekko.japi.function.Procedure2)" }
@apidoc[Flow.doOnCancel](Flow) { scala="#doOnCancel(f:(Throwable,Boolean)=&gt;Unit):FlowOps.this.Repr[Out]" java="#doOnCancel(org.apache.pekko.japi.function.Procedure2)" }

## Description

Run the given function when the downstream cancels.

## Reactive Streams semantics

@@@div { .callout }

**emits** when upstream emits an element

**backpressures** when downstream backpressures

**completes** when upstream completes

**cancels** when downstream cancels

@@@
