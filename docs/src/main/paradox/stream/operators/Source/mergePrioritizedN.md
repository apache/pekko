<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# mergePrioritizedN

Merge multiple sources with priorities.

@ref[Fan-in operators](../index.md#fan-in-operators)

## Signature

@apidoc[Source.mergePrioritizedN](Source$) { scala="#mergePrioritizedN[T](sourcesAndPriorities:Seq[(org.apache.pekko.stream.scaladsl.Source[T,_],Int)],eagerComplete:Boolean):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" java="#mergePrioritizedN(java.util.List,boolean)" }

## Description

Merge multiple sources. Prefer sources depending on priorities if all sources have elements ready. If a subset of all
sources have elements ready the relative priorities for those sources are used to prioritize. For example, when used 
with only three sources `sourceA`, `sourceB` and `sourceC`, the `sourceA` has a probability of `(priorityOfA) / (priorityOfA + priorityOfB + priorityOfC)` of being 
prioritized and similarly for the rest of the sources. The priorities for each source must be positive integers.

## Example
Scala
:   @@snip [FlowMergeSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/FlowMergeSpec.scala) { #mergePrioritizedN }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #mergePrioritizedN }

## Reactive Streams semantics

@@@div { .callout }

**emits** when one of the inputs has an element available, preferring inputs based on their priorities if multiple have elements available

**backpressures** when downstream backpressures

**completes** when all upstreams complete (or when any upstream completes if `eagerComplete=true`.)

**Cancels when** downstream cancels
@@@

