# contramap

Transform this Flow by applying a function to each *incoming* upstream element before it is passed to the Flow.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Flow.contramap](Flow) { scala="#contramap%5BIn2%5D(f%3AIn2%3D%3EIn)%3Aorg.apache.pekko.stream.scaladsl.Flow%5BIn2%2COut%2CMat%5D" java="#contramap(org.apache.pekko.japi.function.Function)" }

## Description

Transform this Flow by applying a function to each *incoming* upstream element before it is passed to the Flow.

## Examples

Scala
:  @@snip [Flow.scala](/docs/src/test/scala/docs/stream/operators/flow/ContraMap.scala) { #imports #contramap }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the mapping function returns an element

**backpressures** '''Backpressures when''' original flow backpressures

**completes** when upstream completes

**cancels** when original flow cancels

@@@