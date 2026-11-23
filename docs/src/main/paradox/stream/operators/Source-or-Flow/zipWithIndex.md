<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# zipWithIndex

Zips elements of current flow with its indices.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Source.zipWithIndex](Source) { scala="#zipWithIndex:FlowOps.this.Repr[(Out,Long)]" java="#zipWithIndex()" }
@apidoc[Flow.zipWithIndex](Flow) { scala="#zipWithIndex:FlowOps.this.Repr[(Out,Long)]" java="#zipWithIndex()" }

## Description

Zips elements of current flow with its indices.

See also:

 * @ref:[zip](zip.md)
 * @ref:[zipAll](zipAll.md)
 * @ref:[zipWith](zipWith.md)  

## Example

Scala
:   @@snip [FlowZipWithIndexSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/FlowZipWithIndexSpec.scala) { #zip-with-index }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #zip-with-index }

## Reactive Streams semantics

@@@div { .callout }

**emits** upstream emits an element and is paired with their index

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
