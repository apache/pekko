<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# map

Transform each element in the stream by calling a mapping function with it and passing the returned value downstream.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.map](Source) { scala="#map[T](f:Out=&gt;T):FlowOps.this.Repr[T]" java="#map(org.apache.pekko.japi.function.Function)" }
@apidoc[Flow.map](Flow) { scala="#map[T](f:Out=&gt;T):FlowOps.this.Repr[T]" java="#map(org.apache.pekko.japi.function.Function)" }

## Description

Transform each element in the stream by calling a mapping function with it and passing the returned value downstream.

## Examples

Scala
:  @@snip [Flow.scala](/docs/src/test/scala/docs/stream/operators/Map.scala) { #imports #map }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the mapping function returns an element

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
