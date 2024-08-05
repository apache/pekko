# optionalVia

For a stream containing optional elements, transforms each element by applying the given `viaFlow` and passing the value downstream as an optional value.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.optionalVia](Source$) { scala="#optionalVia%5BSOut,FOut,SMat,FMat,Mat](source:org.apache.pekko.stream.scaladsl.Source%5BOption%5BSOut],SMat],viaFlow:org.apache.pekko.stream.scaladsl.Flow%5BSOut,FOut,FMat])(combine:(SMat,FMat)=%3EMat):org.apache.pekko.stream.scaladsl.Source%5BOption%5BFOut],Mat]" java="#optionalVia(org.apache.pekko.stream.javadsl.Source,org.apache.pekko.stream.javadsl.Flow,org.apache.pekko.japi.function.Function2)" }
@apidoc[Flow.optionalVia](Flow$) { scala="#optionalVia%5BFIn,FOut,FViaOut,FMat,FViaMat,Mat](flow:org.apache.pekko.stream.scaladsl.Flow%5BFIn,Option%5BFOut],FMat],viaFlow:org.apache.pekko.stream.scaladsl.Flow%5BFOut,FViaOut,FViaMat])(combine:(FMat,FViaMat)=%3EMat):org.apache.pekko.stream.scaladsl.Flow%5BFIn,Option%5BFViaOut],Mat]" java="#optionalVia(org.apache.pekko.stream.javadsl.Flow,org.apache.pekko.stream.javadsl.Flow,org.apache.pekko.japi.function.Function2)" }

## Description

For a stream containing optional elements, transforms each element by applying
the given `viaFlow` and passing the value downstream as an optional value.

Scala
:  @@snip [OptionalVia.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/OptionalVia.scala) { #optionalVia }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #optionalVia }

## Reactive Streams semantics

@@@div { .callout }

**emits** while the provided viaFlow is runs with defined elements

**backpressures** when the viaFlow runs for the defined elements and downstream backpressures

**completes** when the upstream completes

@@@

