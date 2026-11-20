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

# Sink.exists

A `Sink` that will test the given predicate `p` for every received element and completes with the result.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.exists](Sink$) { scala="#exists[T](p:T=%3EBoolean):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[Boolean]]" java="#exists(org.apache.pekko.japi.function.Predicate)" }

## Description
`exists` applies a predicate function to assert each element received, it returns true if any elements satisfy the assertion, otherwise it returns false.

It materializes into a `Future` (in Scala) or a `CompletionStage` (in Java) that completes with the last state when the stream has finished.

Note that if source is empty, it will return false

A `Sink` that will test the given predicate `p` for every received element and

- completes and returns  @scala[`Future`] @java[`CompletionStage`] of `true` if the predicate is true for any element;
- completes and returns  @scala[`Future`] @java[`CompletionStage`] of `false` if the stream is empty (i.e. completes before signalling any elements);
- completes and returns  @scala[`Future`] @java[`CompletionStage`] of `false` if the predicate is false for all elements.

The materialized value @scala[`Future`] @java[`CompletionStage`] will be completed with the value `true` or `false`
when the input stream ends, or completed with `Failure` if there is a failure signaled in the stream.

## Example

This example tests any element in the stream is `>` 3.

Scala
:   @@snip [exists.scala](/docs/src/test/scala/docs/stream/operators/sink/Exists.scala) { #exists }

Java
:   @@snip [exists.java](/docs/src/test/java/jdocs/stream/operators/sink/Exists.java) { #exists }

## Reactive Streams Semantics

@@@div { .callout }

***Completes*** when upstream completes or the predicate `p` returns `true`

**cancels** when predicate `p` returns `true`

**backpressures** when the invocation of predicate `p` has not yet completed

@@@
