<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# detach

Detach upstream demand from downstream demand without detaching the stream rates.

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.detach](Source) { scala="#detach:FlowOps.this.Repr[Out]" java="#detach()" }
@apidoc[Flow.detach](Flow) { scala="#detach:FlowOps.this.Repr[Out]" java="#detach()" }

## Description

Detach upstream demand from downstream demand without detaching the stream rates.

## Reactive Streams semantics

@@@div { .callout }

**emits** when the upstream operators have emitted and there is demand

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@

