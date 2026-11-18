<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# dropWithin

Drop elements until a timeout has fired

@ref[Timer driven operators](../index.md#timer-driven-operators)

## Signature

@apidoc[Source.dropWithin](Source) { scala="#dropWithin(d:scala.concurrent.duration.FiniteDuration):FlowOps.this.Repr[Out]" java="#dropWithin(java.time.Duration)" }
@apidoc[Flow.dropWithin](Flow) { scala="#dropWithin(d:scala.concurrent.duration.FiniteDuration):FlowOps.this.Repr[Out]" java="#dropWithin(java.time.Duration)" }


## Description

Drop elements until a timeout has fired

## Reactive Streams semantics

@@@div { .callout }

**emits** after the timer fired and a new upstream element arrives

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@

