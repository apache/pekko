# Source.elements

Create a `Source` from the given elements.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.elements](Source$) { scala="#elements[T](elements:T*):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#elements[T](T)" }


## Description

Create a `Source` from the given elements.

## Examples

## Reactive Streams semantics

@@@div { .callout }

**emits** the elements one by one

**completes** when the last element has been emitted

@@@
