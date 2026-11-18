<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Sink.never

Always backpressure never cancel and never consume any elements from the stream.

@ref[Sink operators](../index.md#sink-operators)

## Signature

@apidoc[Sink.never](Sink$) { java="#never()" }
@apidoc[Sink.never](Sink$) { scala="#never:org.apache.pekko.stream.scaladsl.Sink[Any,scala.concurrent.Future[org.apache.pekko.Done]]" }


## Description

A `Sink` that will always backpressure never cancel and never consume any elements from the stream.

## Reactive Streams semantics

@@@div { .callout }

**cancels** never

**backpressures** always

@@@


