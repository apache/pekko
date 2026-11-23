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

# groupedAdjacentBy

Partitions this stream into chunks by a delimiter function.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.groupedAdjacentBy](Source) { scala="#groupedAdjacentBy[T](f:Out=&gt;T):FlowOps.this.Repr[Seq[Out]]" java="#groupedAdjacentBy(org.apache.pekko.japi.function.Function)" }
@apidoc[Flow.groupedAdjacentBy](Flow) { scala="#groupedAdjacentBy[T](f:Out=&gt;T):FlowOps.this.Repr[Seq[Out]]" java="#groupedAdjacentBy(org.apache.pekko.japi.function.Function)" }


## Description

Partitions this stream into chunks by a delimiter function.

Adheres to the ActorAttributes.SupervisionStrategy attribute (applied to the key function). On `Supervision.Resume` the offending element is skipped; on `Supervision.Restart` the current group is dropped.

See also:

* @ref[groupedAdjacentByWeighted](groupedAdjacentByWeighted.md) for a variant that groups with weight limit too.

## Examples

The example below demonstrates how `groupedAdjacentBy` partitions the elements into @scala[`Seq`] @java[`List`].

Scala
:  @@snip [GroupedAdjacentBy.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/GroupedAdjacentBy.scala) { #groupedAdjacentBy }

Java
:  @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #groupedAdjacentBy }

## Reactive Streams semantics

@@@div { .callout }

**emits** when the delimiter function returns a different value than the previous element's result

**backpressures** when a chunk has been assembled and downstream backpressures

**completes** when upstream completes

@@@


