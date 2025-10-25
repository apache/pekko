# doOnFirst

Run the given function when the first element is received.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.doOnFirst](Source) { scala="#doOnFirst(f:Out=&gt;Unit):FlowOps.this.Repr[Out]" java="#doOnFirst(org.apache.pekko.japi.function.Procedure)" }
@apidoc[Flow.doOnFirst](Flow) { scala="#doOnFirst(f:Out=&gt;Unit):FlowOps.this.Repr[Out]" java="#doOnFirst(org.apache.pekko.japi.function.Procedure)" }

## Description

Run the given function when the first element is received.

## Examples

Scala
:  @@snip [Flow.scala](/docs/src/test/scala/docs/stream/operators/DoOnFirst.scala) { #imports #doOnFirst }

## Reactive Streams semantics

@@@div { .callout }

**emits** when upstream emits an element

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
