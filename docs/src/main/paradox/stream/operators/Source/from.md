<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# @scala[Source.apply]@java[Source.from]

Stream the values of an @scala[`Seq`]@java[`Iterable`].

@ref[Source operators](../index.md#source-operators)

## Signature

@@@div { .group-scala }

@apidoc[Source.apply](Source$) { scala="#apply[T](iterable:scala.collection.immutable.Iterable[T]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]"  }

@@@ 

@@@div { .group-java }

@apidoc[Source.from](Source$) { java="#from(java.lang.Iterable)" }

@@@ 

## Description

Stream the values of an @scala[`Seq`]@java[`Iterable`]. @java[Make sure the `Iterable` is immutable or at least not modified after being used
as a source. Otherwise the stream may fail with `ConcurrentModificationException` or other more subtle errors may occur.]

## Examples

Java
:  @@snip [from.java](/docs/src/test/java/jdocs/stream/operators/SourceDocExamples.java) { #imports #source-from-example }

## Reactive Streams semantics

@@@div { .callout }

**emits** the next value of the seq

**completes** when the last element of the seq has been emitted

@@@
