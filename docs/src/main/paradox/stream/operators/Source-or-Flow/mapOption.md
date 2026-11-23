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

# mapOption

Transform each element in the stream by calling a mapping function with it and emits the contained item if present.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.mapOption](Source) { scala="#mapOption[T](f:Out=&gt;scala.Option[T]):FlowOps.this.Repr[T]" java="#mapOption(org.apache.pekko.japi.function.Function)" }
@apidoc[Flow.mapOption](Flow) { scala="#mapOption[T](f:Out=&gt;scala.Option[T]):FlowOps.this.Repr[T]" java="#mapOption(org.apache.pekko.japi.function.Function)" }

## Description

Transform each element in the stream by calling a mapping function with it and emits the contained item if present.

## Examples

Scala
:  @@snip [Flow.scala](/docs/src/test/scala/docs/stream/operators/MapOption.scala) { #imports #mapOption }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the mapping function returns an element present

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@
