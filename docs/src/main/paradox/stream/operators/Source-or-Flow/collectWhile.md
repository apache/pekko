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

# collectWhile

Transform this stream by applying the given partial function to each of the elements on which the function is defined as they pass through this processing step, and cancel the upstream publisher after the partial function is not applied.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.collectWhile](Source) { scala="#collectWhile[T](pf:PartialFunction[Out,T]):FlowOps.this.Repr[T]" java="#collectWhile(scala.PartialFunction)" }
@apidoc[Flow.collectWhile](Flow) { scala="#collectWhile[T](pf:PartialFunction[Out,T]):FlowOps.this.Repr[T]" java="#collectWhile(scala.PartialFunction)" }


## Description

Transform this stream by applying the given partial function to each of the elements on which the function is defined 
as they pass through this processing step, and cancel the upstream publisher after the partial function is not applied.

## Example

Scala
:  @@snip [Collect.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Collect.scala) { #collectWhile }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #collectWhile }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the provided partial function is defined for the element

**backpressures** when the partial function is defined for the element and downstream backpressures

**completes** when upstream completes or the partial function is not applied

**cancels** when downstream cancels

@@@
