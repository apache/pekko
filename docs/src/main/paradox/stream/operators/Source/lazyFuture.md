<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Source.lazyFuture

Defers creation of a future of a single element source until there is demand.

@ref[Source operators](../index.md#source-operators)

## Signature

@apidoc[Source.lazyFuture](Source$) { scala="#lazyFuture[T](create:()=&gt;scala.concurrent.Future[T]):org.apache.pekko.stream.scaladsl.Source[T,org.apache.pekko.NotUsed]" }

## Description

Invokes the user supplied factory when the first downstream demand arrives. When the returned future completes 
successfully the value is emitted downstream as a single stream element. If the future or the factory fails the 
stream is failed.

Note that asynchronous boundaries (and other operators) in the stream may do pre-fetching which counter acts
the laziness and will trigger the factory immediately.

## Reactive Streams semantics

@@@div { .callout }

**emits** when there is downstream demand and the element factory returned future has completed

**completes** after emitting the single element

@@@

