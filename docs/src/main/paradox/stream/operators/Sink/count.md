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

# Sink.count

Counts all incoming elements until upstream terminates.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.count](Sink$) { scala="#count[T]:org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[Long]]" java="#count()" }


## Description

Counts values emitted from the stream, the count is available through a @scala[`Future`] @java[`CompletionStage`]
which completes when the stream completes. 

## Example

Given a stream of numbers we can count the numbers with the `count` operator

Scala
:   @@snip [SinkSpec.scala](/stream-tests/src/test/scala/org/apache/pekko/stream/scaladsl/SinkSpec.scala) { #count-operator-example }

Java
:   @@snip [SinkDocExamples.java](/docs/src/test/java/jdocs/stream/operators/SinkDocExamples.java) { #count-operator-example }

## Reactive Streams semantics

@@@div { .callout }

**completes** when upstream completes

**backpressures** never (counting is a lightweight operation)

**cancels** never

@@@


