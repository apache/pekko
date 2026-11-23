<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# interleave

Emits a specifiable number of elements from the original source, then from the provided source and repeats.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Source.interleave](Source) { scala="#interleave[U&gt;:Out](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],_],segmentSize:Int,eagerClose:Boolean):FlowOps.this.Repr[U]" java="#interleave(org.apache.pekko.stream.Graph,int,boolean)" }
@apidoc[Flow.interleave](Flow) { scala="#interleave[U&gt;:Out](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],_],segmentSize:Int,eagerClose:Boolean):FlowOps.this.Repr[U]" java="#interleave(org.apache.pekko.stream.Graph,int,boolean)" }


## Description

Emits a specifiable number of elements from the original source, then from the provided source and repeats. If one
source completes the rest of the other stream will be emitted.

## Example
Scala
:   @@snip [FlowInterleaveSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/FlowInterleaveSpec.scala) { #interleave }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #interleave }

## Reactive Streams semantics

@@@div { .callout }

**emits** when element is available from the currently consumed upstream

**backpressures** when downstream backpressures

**completes** when both upstreams have completed

@@@
