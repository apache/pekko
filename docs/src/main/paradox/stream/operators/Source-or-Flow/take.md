<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# take

Pass `n` incoming elements downstream and then complete

@ref[Simple operators](../index.md#simple-operators)

## Signature

@apidoc[Source.take](Source) { scala="#take(n:Long):FlowOps.this.Repr[Out]" java="#take(long)" }
@apidoc[Flow.take](Flow) { scala="#take(n:Long):FlowOps.this.Repr[Out]" java="#take(long)" }


## Description

Pass `n` incoming elements downstream and then complete

## Example

Scala
:  @@snip [Take.scala](/docs/src/test/scala/docs/stream/operators/sourceorflow/Take.scala) { #take }

Java
:   @@snip [SourceOrFlow.java](/docs/src/test/java/jdocs/stream/operators/SourceOrFlow.java) { #take }


## Reactive Streams semantics

@@@div { .callout }

**emits** while the specified number of elements to take has not yet been reached

**backpressures** when downstream backpressures

**completes** when the defined number of elements has been taken or upstream completes

@@@

