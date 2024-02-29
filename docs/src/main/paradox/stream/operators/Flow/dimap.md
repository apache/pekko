# dimap

Transform this Flow by applying a function `f` to each *incoming* upstream element before it is passed to the Flow, and a function `g` to each *outgoing* downstream element.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Flow.dimap](Flow) { scala="#dimap%5BIn2%2C%20Out2%5D(f%3A%20In2%20%3D%3E%20In)(g%3A%20Out%20%3D%3E%20Out2)%3Aorg.apache.pekko.stream.scaladsl.Flow%5BIn2%2COut2%2CMat%5D" java="#dimap(org.apache.pekko.japi.function.Function,org.apache.pekko.japi.function.Function)" }

## Description

Transform this Flow by applying a function `f` to each *incoming* upstream element before it is passed to the Flow,
and a function `g` to each *outgoing* downstream element.

## Examples

Scala
:  @@snip [DiMap.scala](/docs/src/test/scala/docs/stream/operators/flow/DiMap.scala) { #imports #dimap }

Java
:  @@snip [DiMap.java](/docs/src/test/java/jdocs/stream/operators/flow/DiMap.java) { #imports #dimap }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the mapping function `g` returns an element

**backpressures** '''Backpressures when''' original flow backpressures

**completes** when original flow completes

**cancels** when original flow cancels

@@@