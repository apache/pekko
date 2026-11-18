<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Sink.completionStageSink

Streams the elements to the given future sink once it successfully completes. 

@ref[Sink operators](../index.md#sink-operators)


## Description

Streams the elements through the given future sink once it successfully completes. 
If the future fails the stream is failed.

`completionStageSink` uses the same lazy materialization semantics as
@ref:[lazyCompletionStageSink](lazyCompletionStageSink.md): the nested sink is not materialized until the first
upstream element arrives. If the stream completes before the first element, the materialized value fails with
`org.apache.pekko.stream.NeverMaterializedException`.

If you want this to work for empty streams as well, use
@ref:[eagerCompletionStageSink](eagerCompletionStageSink.md).

## Reactive Streams semantics

@@@div { .callout }

**cancels** if the future fails or if the created sink cancels 

**backpressures** when initialized and when created sink backpressures

@@@


