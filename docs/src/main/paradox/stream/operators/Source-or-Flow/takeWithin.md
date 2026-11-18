<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# takeWithin

Pass elements downstream within a timeout and then complete.

@ref[Timer driven operators](../index.md#timer-driven-operators)

## Signature

@apidoc[Source.takeWithin](Source) { scala="#takeWithin(d:scala.concurrent.duration.FiniteDuration):FlowOps.this.Repr[Out]" java="#takeWithin(java.time.Duration)" }
@apidoc[Flow.takeWithin](Flow) { scala="#takeWithin(d:scala.concurrent.duration.FiniteDuration):FlowOps.this.Repr[Out]" java="#takeWithin(java.time.Duration)" }


## Description

Pass elements downstream within a timeout and then complete.

## Reactive Streams semantics

@@@div { .callout }

**emits** when an upstream element arrives

**backpressures** downstream backpressures

**completes** upstream completes or timer fires

@@@

