# Source.fromArray

Stream the values of an `array`.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.fromArray](Source$) { scala="#apply[T](array:scala.Array[T]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#fromArray(java.lang.Object[])" }

## Description

Stream the values of a Java `array`. 

## Examples

Java
:  @@snip [from.java](/docs/src/test/java/jdocs/stream/operators/SourceDocExamples.java) { #imports #source-from-array }

## Reactive Streams semantics

@@@div { .callout }

**emits** the next value of the array

**completes** when the last element of the seq has been emitted

@@@
