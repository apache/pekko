<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Sink.actorRef

Send the elements from the stream to an `ActorRef` of the classic actors API.

@ref[Actor interop operators](../index.md#actor-interop-operators)

## Signature

@apidoc[Sink.actorRef](Sink$) { scala="#actorRef[T](ref:org.apache.pekko.actor.ActorRef,onCompleteMessage:Any,onFailureMessage:Throwable=&gt;Any):org.apache.pekko.stream.scaladsl.Sink[T,org.apache.pekko.NotUsed]" java="#actorRef(org.apache.pekko.actor.ActorRef,java.lang.Object)" }

## Description

Send the elements from the stream to an `ActorRef`. No backpressure so care must be taken to not overflow the inbox.

See also:

* @ref[`Sink.actorRefWithBackpressure`](../Sink/actorRefWithBackpressure.md) Send elements to an actor with backpressure support
* @ref[`ActorSink.actorRef`](../ActorSink/actorRef.md) The corresponding operator for the new actors API
* @ref[`ActorSink.actorRefWithBackpressure`](../ActorSink/actorRefWithBackpressure.md) Send elements to an actor of the new actors API supporting backpressure

## Reactive Streams semantics

@@@div { .callout }

**cancels** when the actor terminates

**backpressures** never

@@@


