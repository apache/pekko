<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Sink.foldWhile

Fold over emitted elements with a function, where each invocation will get the new element and the result from the previous fold invocation.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.foldWhile](Sink$) { scala="#foldWhile%5BU%2C%20T%5D(zero%3A%20U)(p%3A%20U%20%3D%3E%20Boolean)(f%3A%20(U%2C%20T)%20%3D%3E%20U):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[U]]" java="#foldWhile(java.lang.Object,org.apache.pekko.japi.function.Predicate,org.apache.pekko.japi.function.Function2)" }

## Description

A Sink that will invoke the given function for every received element, giving it its previous output (or the given zero value) 
and the element as input. 

Materializes into a @scala[`Future`] @java[`CompletionStage`] that will complete with the last state when the stream has completed, 
predicate p returns false, or completed with Failure if there is a failure signaled in the stream.

This operator allows combining values into a result without a global mutable state by instead passing the state along
between invocations.

## Example

`foldWhile` is typically used to 'fold up' the incoming values into an aggregate with a predicate.
For example, you can use `foldWhile` to calculate the sum while some predicate is true.

Scala
:   @@snip [FoldWhile.scala](/docs/src/test/scala/docs/stream/operators/sink/FoldWhile.scala) { #foldWhile }

Java
:   @@snip [FoldWhile.java](/docs/src/test/java/jdocs/stream/operators/sink/FoldWhile.java) { #foldWhile }

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** when the previous fold function invocation has not yet completed

@@@

