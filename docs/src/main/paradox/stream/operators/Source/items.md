# Source.items

Create a `Source` from the given items.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.items](Source$) { scala="#items[T](items:T*):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#items[T](T)" }


## Description

Create a `Source` from the given items.

## Examples

## Reactive Streams semantics

@@@div { .callout }

**emits** the items one by one

**completes** when the last item has been emitted

@@@
