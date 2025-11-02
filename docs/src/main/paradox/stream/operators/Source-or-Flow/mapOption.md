# mapOption

Transform each element in the stream by calling a mapping function with it and emits the contained item if present.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.mapOption](Source) { scala="#mapOption[T](f:Out=&gt;scala.Option[T]):FlowOps.this.Repr[T]" java="#mapOption(org.apache.pekko.japi.function.Function)" }
@apidoc[Flow.mapOption](Flow) { scala="#mapOption[T](f:Out=&gt;scala.Option[T]):FlowOps.this.Repr[T]" java="#mapOption(org.apache.pekko.japi.function.Function)" }

## Description

Transform each element in the stream by calling a mapping function with it and emits the contained item if present.

## Examples

Scala
:  @@snip [Flow.scala](/docs/src/test/scala/docs/stream/operators/MapOption.scala) { #imports #mapOption }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the mapping function returns and element present

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
