# Source.cycle

Stream iterator in cycled manner.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.cycle](Source$) { scala="#cycle[T](f:()=&gt;Iterator[T]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#cycle(org.apache.pekko.japi.function.Creator)" }


## Description

Stream iterator in cycled manner. Internally a new iterator is being created to cycle the one provided via argument meaning
when the original iterator runs out of elements to process it will start all over again from the beginning of the iterator
provided by the evaluation of provided parameter. If the method argument provides an empty iterator the stream will be 
terminated with an exception.

## Examples

Scala
:  @@snip [cycle.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/SourceSpec.scala) { #cycle }

Java
:  @@snip [cycle.java](/stream-tests/src/test/java/org/apache/pekko/stream/javadsl/SourceTest.java) { #cycle }


When iterator is empty the stream will be terminated with _IllegalArgumentException_

Scala
:  @@snip [cycleError.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/SourceSpec.scala) { #cycle-error }

Java
:  @@snip [cycle.java](/stream-tests/src/test/java/org/apache/pekko/stream/javadsl/SourceTest.java) { #cycle-error }

## Reactive Streams semantics

@@@div { .callout }

**emits** the next value returned from cycled iterator

**completes** never

@@@
