<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# merge

Merge multiple sources.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Source.merge](Source) { scala="#merge[U&gt;:Out,M](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],M],eagerComplete:Boolean):FlowOps.this.Repr[U]" java="#merge(org.apache.pekko.stream.Graph)" java="#merge(org.apache.pekko.stream.Graph,boolean)" }
@apidoc[Flow.merge](Flow) { scala="#merge[U&gt;:Out,M](that:org.apache.pekko.stream.Graph[org.apache.pekko.stream.SourceShape[U],M],eagerComplete:Boolean):FlowOps.this.Repr[U]" java="#merge(org.apache.pekko.stream.Graph)" java="#merge(org.apache.pekko.stream.Graph,boolean)" }


## Description

Merge multiple sources. Picks elements randomly if all sources has elements ready.

## Example
Scala
:   @@snip [FlowMergeSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/FlowMergeSpec.scala) { #merge }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #merge }

## Reactive Streams semantics

@@@div { .callout }

**emits** when one of the inputs has an element available

**backpressures** when downstream backpressures

**completes** when all upstreams complete (This behavior is changeable to completing when any upstream completes by setting `eagerComplete=true`.)

@@@
