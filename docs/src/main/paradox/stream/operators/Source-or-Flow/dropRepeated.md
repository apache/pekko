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

# dropRepeated

Only pass on those elements that are distinct from the previous element.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.dropRepeated](Source) { scala="#dropRepeated():FlowOps.this.Repr[Out]" java="#dropRepeated()" }
@apidoc[Flow.dropRepeated](Flow) { scala="#dropRepeated():FlowOps.this.Repr[Out]" java="#dropRepeated()" }
@apidoc[Source.dropRepeated](Source) { scala="#dropRepeated(p:(Out,Out)=&gt;Boolean):FlowOps.this.Repr[Out]" java="#dropRepeated(org.apache.pekko.japi.function.Function2)" }
@apidoc[Flow.dropRepeated](Flow) { scala="#dropRepeated(p:(Out,Out)=&gt;Boolean):FlowOps.this.Repr[Out]" java="#dropRepeated(org.apache.pekko.japi.function.Function2)" }


## Description

Only pass on those elements that are distinct from the previous element.

By default elements are compared using equality. An overload accepting a predicate `p` can be used instead,
in which case an element is dropped when `p` returns `true` for the previous element and the current element.

## Example

For example, given a `Source` of numbers, we just want to pass distinct numbers downstream:

Scala
:  @@snip [Filter.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Filter.scala) { #dropRepeated }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #dropRepeated }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the element is distinct from the previous element

**backpressures** when the element is distinct from the previous element and downstream backpressures

**completes** when upstream completes

@@@

## API docs

@apidoc[Flow.dropRepeated](Flow) { scala="#dropRepeated():FlowOps.this.Repr[Out]" java="#dropRepeated()" }
