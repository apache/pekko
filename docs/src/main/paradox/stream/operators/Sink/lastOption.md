<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Sink.lastOption

Materialize a @scala[`Future[Option[T]]`] @java[`CompletionStage<Optional<T>>`] which completes with the last value emitted wrapped in an @scala[`Some`] @java[`Optional`] when the stream completes.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.lastOption](Sink$) { scala="#lastOption[T]:org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[Option[T]]]" java="#lastOption()" }


## Description

Materialize a @scala[`Future[Option[T]]`] @java[`CompletionStage<Optional<T>>`] which completes with the last value
emitted wrapped in an @scala[`Some`] @java[`Optional`] when the stream completes. if the stream completes with no elements the `CompletionStage` is
completed with @scala[`None`] @java[an empty `Optional`].

## Example

Scala
:   @@snip [LastSinkSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/LastSinkSpec.scala) { #lastOption-operator-example }

Java
:   @@snip [SinkDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SinkDocExamples.java) { #lastOption-operator-example }

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** never

@@@
