<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# initialDelay

Delays the initial element by the specified duration.

@ref[Timer driven operators](../index.md#timer-driven-operators)

## Signature

@apidoc[Source.initialDelay](Source$) { scala="#initialDelay(delay:scala.concurrent.duration.FiniteDuration):FlowOps.this.Repr[Out]" java="#initialDelay(java.time.Duration)" }
@apidoc[Flow.initialDelay](Flow) { scala="#initialDelay(delay:scala.concurrent.duration.FiniteDuration):FlowOps.this.Repr[Out]" java="#initialDelay(java.time.Duration)" }


## Description

Delays the initial element by the specified duration.

## Reactive Streams semantics

@@@div { .callout }

**emits** when upstream emits an element if the initial delay is already elapsed

**backpressures** when downstream backpressures or initial delay is not yet elapsed

**completes** when upstream completes

**cancels** when downstream cancels

@@@

