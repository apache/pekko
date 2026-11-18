<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Source.completionStage

Send the single value of the `CompletionStage` when it completes and there is demand.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.completionStage](Source$) { scala="#completionStage[T](completionStage:java.util.concurrent.CompletionStage[T]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#completionStage(java.util.concurrent.CompletionStage)" }


## Description

Send the single value of the `CompletionStage` when it completes and there is demand.
If the `CompletionStage` completes with `null` stage is completed without emitting a value.
If the `CompletionStage` fails the stream is failed with that exception.

For the corresponding operator for the Scala standard library `Future` see @ref:[future](future.md).

## Example

Java
:  @@snip [SourceFromCompletionStage.java](/docs/src/test/java/jdocs/stream/operators/source/FromCompletionStage.java) { #sourceFromCompletionStage }

## Reactive Streams semantics

@@@div { .callout }

**emits** the future completes

**completes** after the future has completed

@@@
