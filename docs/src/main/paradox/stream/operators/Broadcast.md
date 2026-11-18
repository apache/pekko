<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Broadcast

Emit each incoming element to each of `n` outputs.

@ref[Fan-out operators](index.md#fan-out-operators)

## Signature

@apidoc[stream.*.Broadcast]

## Description

Emit each incoming element to each of `n` outputs.

## Example

Here is an example that is using `Broadcast` to aggregate different values from a `Source` of integers.

Scala
:   @@snip [BroadcastDocExample.scala](/docs/src/test/scala/docs/stream/operators/BroadcastDocExample.scala) { #broadcast }

Java
:   @@snip [BroadcastDocExample.java](/docs/src/test/java/jdocs/stream/operators/BroadcastDocExample.java) { #import #broadcast }

Note that asynchronous boundary for the output streams must be added explicitly if it's desired to run them in parallel.

Scala
:   @@snip [BroadcastDocExample.scala](/docs/src/test/scala/docs/stream/operators/BroadcastDocExample.scala) { #broadcast-async }

Java
:   @@snip [BroadcastDocExample.java](/docs/src/test/java/jdocs/stream/operators/BroadcastDocExample.java) { #broadcast-async }

 

## Reactive Streams semantics

@@@div { .callout }

**emits** when all of the outputs stops backpressuring and there is an input element available

**backpressures** when any of the outputs backpressures

**completes** when upstream completes

**cancels** depends on the `eagerCancel` flag. If it is true, when any downstream cancels, if false, when all downstreams cancel.

@@@


