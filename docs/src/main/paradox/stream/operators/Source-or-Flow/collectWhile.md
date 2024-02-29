# collectWhile

Transform this stream by applying the given partial function to each of the elements on which the function is defined as they pass through this processing step, and cancel the upstream publisher after the partial function is not applied.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.collectWhile](Source) { scala="#collectWhile[T](pf:PartialFunction[Out,T]):FlowOps.this.Repr[T]" java="#collectWhile(scala.PartialFunction)" }
@apidoc[Flow.collectWhile](Flow) { scala="#collectWhile[T](pf:PartialFunction[Out,T]):FlowOps.this.Repr[T]" java="#collectWhile(scala.PartialFunction)" }


## Description

Transform this stream by applying the given partial function to each of the elements on which the function is defined 
as they pass through this processing step, and cancel the upstream publisher after the partial function is not applied.

## Example

Scala
:  @@snip [Collect.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Collect.scala) { #collectWhile }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #collectWhile }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the provided partial function is defined for the element

**backpressures** when the partial function is defined for the element and downstream backpressures

**completes** when upstream completes or the partial function is not applied

**cancels** when downstream cancels

@@@
