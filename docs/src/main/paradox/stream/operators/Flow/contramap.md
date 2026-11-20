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

# contramap

Transform this Flow by applying a function to each *incoming* upstream element before it is passed to the Flow.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Flow.contramap](Flow) { scala="#contramap%5BIn2%5D(f%3AIn2%3D%3EIn)%3Aorg.apache.pekko.stream.scaladsl.Flow%5BIn2%2COut%2CMat%5D" java="#contramap(org.apache.pekko.japi.function.Function)" }

## Description

Transform this Flow by applying a function to each *incoming* upstream element before it is passed to the Flow.

## Examples

Scala
:  @@snip [Flow.scala](/docs/src/test/scala/docs/stream/operators/flow/ContraMap.scala) { #imports #contramap }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the mapping function returns an element

**backpressures** when original flow backpressures

**completes** when upstream completes

**cancels** when original flow cancels

@@@