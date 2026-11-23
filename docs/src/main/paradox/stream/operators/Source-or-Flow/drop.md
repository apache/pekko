<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# drop

Drop `n` elements and then pass any subsequent element downstream.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.drop](Source) { scala="#drop(n:Long):FlowOps.this.Repr[Out]" java="#drop(long)" }
@apidoc[Flow.drop](Flow) { scala="#drop(n:Long):FlowOps.this.Repr[Out]" java="#drop(long)" }


## Description

Drop `n` elements and then pass any subsequent element downstream.

## Example

Given a `Source` of numbers we can drop the first 3 elements with the `drop` operator: 

Scala
:  @@snip [Drop.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Drop.scala) { #drop }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #drop }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the specified number of elements has been dropped already

**backpressures** when the specified number of elements has been dropped and downstream backpressures

**completes** when upstream completes

@@@

