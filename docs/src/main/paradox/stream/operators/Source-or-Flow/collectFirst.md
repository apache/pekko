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

# collectFirst

Transform this stream by applying the given partial function to the first element on which the function is defined as it passes through this processing step, and cancel the upstream publisher after the first element is emitted.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.collectFirst](Source) { scala="#collectFirst[T](pf:PartialFunction[Out,T]):FlowOps.this.Repr[T]" java="#collectFirst(scala.PartialFunction)" }
@apidoc[Flow.collectFirst](Flow) { scala="#collectFirst[T](pf:PartialFunction[Out,T]):FlowOps.this.Repr[T]" java="#collectFirst(scala.PartialFunction)" }


## Description

Transform this stream by applying the given partial function to the first element on which the function is defined as 
it pass through this processing step, and cancel the upstream publisher after the first element is emitted.

## Example

Scala
:  @@snip [Collect.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Collect.scala) { #collectFirst }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #collectFirst }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the provided partial function is defined for the first element

**backpressures** when the partial function is defined for the element and downstream backpressures

**completes** upstream completes or the first element is emitted

**cancels** when downstream cancels

@@@
