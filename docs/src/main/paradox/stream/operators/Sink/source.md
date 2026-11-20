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

# Sink.source

A `Sink` that materializes this `Sink` itself as a `Source`, the returning `Source` can only have one subscriber.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.source](Sink$) { java="#source()" }
@apidoc[Sink.source](Sink$) { scala="#source[T]:org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]]" }


## Description

A `Sink` that materialize this `Sink` itself as a `Source`, the returning `Source` can only have one subscriber.

Use `BroadcastHub.sink` if you need a `Source` that allows multiple subscribers.

## Reactive Streams semantics

@@@div { .callout }

**cancels** When the materialized `Source` is cancelled or timeout with subscription.

**backpressures** When the materialized `Source` backpressures or not ready to receive elements.

@@@


