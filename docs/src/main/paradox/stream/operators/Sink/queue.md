<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Sink.queue

Materialize a `SinkQueue` that can be pulled to trigger demand through the sink.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.queue](Sink$) { scala="#queue[T](maxConcurrentPulls:Int):org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.stream.scaladsl.SinkQueueWithCancel[T]]" java="#queue(int)" }


## Description

Materialize a `SinkQueue` that can be pulled to trigger demand through the sink. The queue contains
a buffer in case stream emitting elements faster than queue pulling them.


## Reactive Streams semantics

@@@div { .callout }

**cancels** when  `SinkQueue.cancel` is called

**backpressures** when the buffer is full

@@@

