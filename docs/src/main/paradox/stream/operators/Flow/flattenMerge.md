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

# flattenMerge

Flattens a stream of Source into a single output stream by merging.

@ref[Nesting and flattening operators](../index.md#nesting-and-flattening-operators)

## Signature

@apidoc[Flow.flattenMerge](Flow) { scala="#flattenMerge%5BT%2C%20M%5D(breadth%3A%20Int)(implicit%20ev%3A%20Out%20%3C%3A%3C%20Graph%5BSourceShape%5BT%5D%2C%20M%5D):FlowOps.this.Repr[T]" } 

## Description

Flattens a stream of `Source` into a single output stream by merging, where at most breadth substreams are being consumed 
at any given time. This function is equivalent to `flatMapMerge(breadth, identity)`.
Emits when a currently consumed substream has an element available

## Reactive Streams semantics

@@@div { .callout }

**emits** when one of the currently consumed substreams has an element available

**backpressures** when downstream backpressures or the max number of substreams is reached

**completes** when upstream completes and all consumed substreams complete

@@@


