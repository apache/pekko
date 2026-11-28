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

# Sink.eagerFutureSink

Materializes the inner sink when the future completes, even if no elements have arrived yet.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.eagerFutureSink](Sink$) { scala="#eagerFutureSink[T,M](future:scala.concurrent.Future[org.apache.pekko.stream.scaladsl.Sink[T,M]]):org.apache.pekko.stream.scaladsl.Sink[T,scala.concurrent.Future[M]]" }


## Description

Turn a `Future[Sink]` into a Sink that will consume the values of the source when the future completes
successfully. If the `Future` is completed with a failure the stream is failed.

Unlike @ref:[futureSink](futureSink.md) and @ref:[lazyFutureSink](lazyFutureSink.md), this operator materializes the inner sink as soon as the future
completes, even if no elements have arrived yet. This means empty streams complete normally rather than failing
with `NeverMaterializedException`. At most one element is prefetched before the inner sink requests it.
If upstream completes with an element buffered, that element is delivered on demand before completion.

The materialized future value is completed with the materialized value of the inner sink once it has been
materialized, or failed if the future itself fails or if materialization of the inner sink fails. Upstream
failures or downstream cancellations that occur before the inner sink is materialized are propagated through
the inner sink rather than failing the materialized value directly.

See also @ref:[futureSink](futureSink.md), @ref:[lazyFutureSink](lazyFutureSink.md).

## Reactive Streams semantics

@@@div { .callout }

**cancels** if the future fails or if the created sink cancels 

**backpressures** when initialized and when created sink backpressures

@@@

